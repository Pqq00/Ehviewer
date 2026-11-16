// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.download

import app.eros.shared.data.EhApi
import app.eros.shared.data.PageBatches
import app.eros.shared.data.createEhApi
import app.eros.shared.settings.AppSettings
import com.hippo.ehviewer.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * 下载管理器（进程级单例）。
 *
 * 调度：
 *  - 画廊并发 = `AppSettings.downloadGalleryConcurrency`（默认 2）
 *  - 单画廊页并发 = `AppSettings.downloadPageConcurrency`（默认 3）
 *  - 单页重试 3 次，退避 1s/2s/4s
 *
 * 每页流水线：页地址（PageBatches）→ 图片 URL（readerPage）→ 字节（imageBytes）
 * → 图片魔数校验 → `.part` 临时文件 → 原子 rename。
 *
 * 整本状态机：QUEUED → RUNNING → VERIFYING → COMMITTING → DONE；
 * 网络类失败 → RETRY_WAIT（5s/10s/20s，最多 3 次）→ QUEUED。
 */
object DownloadManager {

    private const val PAGE_TIMEOUT_MS = 30_000L
    private const val MAX_ATTEMPTS = 3
    private const val MAX_GALLERY_AUTO_RETRIES = 3
    private const val RETRY_BASE_MS = 5_000L
    private const val RETRY_MAX_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val api: EhApi = createEhApi()

    private val _tasks = MutableStateFlow<List<DownloadManifest>>(emptyList())
    val tasks: StateFlow<List<DownloadManifest>> = _tasks

    private val running = ConcurrentHashMap.newKeySet<Long>()
    private val initMutex = Mutex()

    private val _ready = MutableStateFlow(false)

    /** 冷启动恢复是否已完成（UI 用来显示加载态）。 */
    val ready: StateFlow<Boolean> = _ready

    /**
     * 冷启动恢复：读取清单、校验页文件、重建队列。
     *
     * 目录扫描和清单回写全部放在 IO 线程；每个画廊只列一次目录
     * （`pageFileSizes`），避免逐页 `listFiles()` 阻塞主线程导致 ANR。
     */
    suspend fun ensureInitialized() {
        if (_ready.value) return
        initMutex.withLock {
            if (_ready.value) return
            val restored = withContext(Dispatchers.IO) {
                val started = System.currentTimeMillis()
                val sizesByGid = HashMap<Long, Map<Int, Long>>()
                val manifests = DownloadStore.listManifests()
                    .map { manifest ->
                        val sizes = sizesByGid.getOrPut(manifest.gid) {
                            DownloadStore.pageFileSizes(manifest.gid)
                        }
                        // 校验每页文件：缺失/过小 → PENDING；上次被杀留下的中间态 → QUEUED 续传
                        manifest.reconciled { index -> sizes[index] ?: 0L }
                    }
                    .sortedByDescending { it.savedAt }
                manifests.forEach { DownloadStore.writeManifestIfChanged(it) }
                AppLog.i(
                    "Download",
                    "restored ${manifests.size} galleries " +
                        "in ${System.currentTimeMillis() - started}ms"
                )
                manifests
            }
            _tasks.value = restored
            _ready.value = true
            DownloadedStore.update(restored)
            schedule()
        }
    }

    fun enqueue(
        gid: Long,
        token: String,
        title: String,
        totalPages: Int,
        thumb: String? = null,
        uploader: String? = null,
        artist: String? = null,
        category: Int = -1,
        titleJpn: String? = null,
    ) {
        if (!_ready.value) {
            scope.launch {
                ensureInitialized()
                enqueue(gid, token, title, totalPages, thumb, uploader, artist, category, titleJpn)
            }
            return
        }
        if (_tasks.value.any { it.gid == gid }) {
            AppLog.d("Download", "already in queue gid=$gid")
            return
        }
        val manifest = DownloadManifest.create(gid, token, title, totalPages, thumb, uploader, artist, category, titleJpn)
        DownloadStore.writeManifest(manifest)
        _tasks.update { listOf(manifest) + it }
        AppLog.i("Download", "enqueued gid=$gid pages=$totalPages")
        schedule()
    }

    fun pause(gid: Long) {
        updateManifest(gid) { it.copy(status = DownloadStatus.PAUSED) }
        schedule()
    }

    fun resume(gid: Long) {
        updateManifest(gid) {
            if (it.status == DownloadStatus.DONE) it
            else it.copy(status = DownloadStatus.QUEUED, error = null, retryCount = 0)
        }
        schedule()
    }

    fun retry(gid: Long) {
        updateManifest(gid) {
            resetFailedPages(it).copy(status = DownloadStatus.QUEUED, error = null, retryCount = 0)
        }
        schedule()
    }

    fun remove(gid: Long, deleteFiles: Boolean) {
        running.remove(gid)
        if (deleteFiles) DownloadStore.deleteGallery(gid)
        _tasks.update { list -> list.filterNot { it.gid == gid } }
        DownloadedStore.refresh()
        AppLog.i("Download", "removed gid=$gid deleteFiles=$deleteFiles")
        schedule()
    }

    /** 已完成页的本地文件（离线阅读用）。 */
    fun localPage(gid: Long, index: Int): ByteArray? {
        val manifest = _tasks.value.firstOrNull { it.gid == gid } ?: DownloadStore.readManifest(gid) ?: return null
        val page = manifest.pages.getOrNull(index) ?: return null
        if (page.status != PageStatus.DONE) return null
        return DownloadStore.readPage(gid, index)
    }

    fun isDownloaded(gid: Long): Boolean =
        _tasks.value.firstOrNull { it.gid == gid }?.status == DownloadStatus.DONE

    fun taskOf(gid: Long): DownloadManifest? =
        _tasks.value.firstOrNull { it.gid == gid }

    // ---------------------------------------------------------------- 调度

    private fun schedule() {
        scope.launch {
            val limit = AppSettings.downloadGalleryConcurrency.coerceIn(1, 3)
            while (running.size < limit) {
                val next = _tasks.value
                    .filter { it.status == DownloadStatus.QUEUED && it.gid !in running }
                    .minByOrNull { it.savedAt }
                    ?: break
                DownloadForeground.start()
                launchGallery(next.gid)
            }
            if (running.isEmpty() && _tasks.value.none { it.status.isBusy }) {
                DownloadForeground.stop()
            }
        }
    }

    private fun launchGallery(gid: Long) {
        if (!running.add(gid)) return
        updateManifest(gid) { it.copy(status = DownloadStatus.RUNNING, error = null) }
        scope.launch {
            val result = runCatching { downloadGallery(gid) }
            running.remove(gid)
            result.onFailure { e ->
                AppLog.w("Download", "gallery $gid crashed: ${e.message}")
                updateManifest(gid) { it.copy(status = DownloadStatus.FAILED, error = e.message) }
            }
            schedule()
        }
    }

    // ---------------------------------------------------------------- 单本

    private suspend fun downloadGallery(gid: Long) {
        var manifest = taskOf(gid) ?: return
        if (manifest.status == DownloadStatus.PAUSED) return
        val total = manifest.totalPages
        val pageConcurrency = AppSettings.downloadPageConcurrency.coerceIn(1, 5)
        val semaphore = Semaphore(pageConcurrency)
        val results = ConcurrentHashMap<Int, DownloadPage>()
        var lastPersist = 0L

        coroutineScope {
            for (page in manifest.pages.filter { it.status != PageStatus.DONE }) {
                if (isAborted(gid)) return@coroutineScope
                launch {
                    semaphore.withPermit {
                        if (isAborted(gid)) return@withPermit
                        results[page.index] = downloadOnePage(gid, manifest.token, page, total)
                        val now = System.currentTimeMillis()
                        if (now - lastPersist > 2000) {
                            lastPersist = now
                            persistProgress(gid, results)
                        }
                    }
                }
            }
        }

        if (isAborted(gid)) return
        persistProgress(gid, results)

        // ---- VERIFYING：以文件系统为准，把"标记 DONE 但文件缺失/过小"的页打回 PENDING
        updateManifest(gid) { it.copy(status = DownloadStatus.VERIFYING, error = null) }
        val beforeVerify = taskOf(gid) ?: return
        val verifiedSizes = withContext(Dispatchers.IO) { DownloadStore.pageFileSizes(gid) }
        val verified = beforeVerify.reconciled { index -> verifiedSizes[index] ?: 0L }
        replaceManifest(gid, verified)
        if (isAborted(gid)) return

        val remaining = verified.pages.count { it.status != PageStatus.DONE }
        if (remaining > 0) {
            finishWithFailures(gid, verified)
            return
        }

        // ---- COMMITTING：清单原子落盘后才算 DONE
        updateManifest(gid) { it.copy(status = DownloadStatus.COMMITTING) }
        if (isAborted(gid)) return
        val committed = (taskOf(gid) ?: verified).copy(
            status = DownloadStatus.DONE,
            error = null,
            retryCount = 0,
            finishedAt = System.currentTimeMillis(),
        )
        replaceManifest(gid, committed)
        DownloadedStore.refresh()
        AppLog.i("Download", "gid=$gid finished DONE ${verified.donePages}/${verified.totalPages}")
    }

    /** 还有页没成功：按失败类型决定自动退避重试 / 暂停 / 失败。 */
    private suspend fun finishWithFailures(gid: Long, manifest: DownloadManifest) {
        val done = manifest.donePages
        val failed = manifest.failedPages
        val failures = manifest.pages.filter { it.status == PageStatus.FAILED }
        val transient = done == 0 && failures.isNotEmpty() && failures.all { isTransientError(it.error) }

        if (transient && manifest.retryCount < MAX_GALLERY_AUTO_RETRIES) {
            val attempt = manifest.retryCount + 1
            val waitMs = (RETRY_BASE_MS shl (attempt - 1)).coerceAtMost(RETRY_MAX_MS)
            updateManifest(gid) {
                it.copy(
                    status = DownloadStatus.RETRY_WAIT,
                    retryCount = attempt,
                    error = "网络异常，${waitMs / 1000} 秒后自动重试（$attempt/$MAX_GALLERY_AUTO_RETRIES）",
                )
            }
            if (waitForRetry(gid, waitMs)) return
            updateManifest(gid) { it.copy(status = DownloadStatus.QUEUED, error = null) }
            schedule()
            return
        }

        val looksLikeNetworkTrouble = done == 0 && failed >= 3
        updateManifest(gid) {
            it.copy(
                status = if (looksLikeNetworkTrouble) DownloadStatus.PAUSED else DownloadStatus.FAILED,
                error = when {
                    looksLikeNetworkTrouble -> "网络异常，已暂停（点击继续重试）"
                    failed > 0 -> "$failed 页下载失败"
                    else -> "还有 ${it.pages.count { p -> p.status != PageStatus.DONE }} 页未完成"
                },
            )
        }
        AppLog.w("Download", "gid=$gid finished with $failed failed pages")
    }

    /** 等待退避；返回 true 表示被暂停/取消，不应再调度。 */
    private suspend fun waitForRetry(gid: Long, durationMs: Long): Boolean {
        var waited = 0L
        while (waited < durationMs) {
            delay(500)
            waited += 500
            val task = taskOf(gid) ?: return true
            if (task.status != DownloadStatus.RETRY_WAIT) return true
        }
        return false
    }

    private suspend fun downloadOnePage(
        gid: Long,
        token: String,
        page: DownloadPage,
        totalPages: Int,
    ): DownloadPage {
        var attempts = 0
        var lastError: String? = null
        while (attempts < MAX_ATTEMPTS) {
            if (isAborted(gid)) return page
            attempts++
            val result = runCatching { fetchPageBytes(gid, token, page.index, totalPages) }
            val bytes = result.getOrNull()
            if (bytes != null && validate(bytes)) {
                val written = DownloadStore.writePage(gid, page.index, extensionOf(page.file ?: "page.jpg"), bytes)
                if (written > 0) {
                    return page.copy(
                        status = PageStatus.DONE,
                        bytes = written,
                        attempts = attempts,
                        error = null,
                    )
                }
                lastError = "写文件失败"
            } else {
                lastError = result.exceptionOrNull()?.message ?: "图片校验失败"
            }
            if (attempts < MAX_ATTEMPTS) delay(1000L shl (attempts - 1))
        }
        AppLog.w("Download", "page ${page.index + 1}/$totalPages FAILED: $lastError")
        return page.copy(status = PageStatus.FAILED, attempts = attempts, error = lastError)
    }

    private suspend fun fetchPageBytes(gid: Long, token: String, index: Int, totalPages: Int): ByteArray {
        val urls = PageBatches.fetch(gid, token, index, totalPages)
        val pageUrl = urls[index] ?: throw IllegalStateException("页地址缺失：第 ${index + 1} 页")
        val page = api.readerPage(pageUrl)
        return withTimeoutOrNull(PAGE_TIMEOUT_MS) {
            api.imageBytes(page.imageUrl) { }
        } ?: throw IllegalStateException("下载超时")
    }

    private fun validate(bytes: ByteArray): Boolean =
        bytes.size >= MIN_VALID_PAGE_BYTES && looksLikeImage(bytes) && !looksLikeHtmlError(bytes)

    /** 写文件/校验/页地址类错误重试无意义；网络、超时、HTTP 状态都算可重试。 */
    private fun isTransientError(error: String?): Boolean {
        val message = error ?: return true
        return !message.contains("写文件失败") &&
            !message.contains("图片校验失败") &&
            !message.contains("页地址缺失")
    }

    private fun isAborted(gid: Long): Boolean =
        _tasks.value.firstOrNull { it.gid == gid }
            ?.let { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED } ?: true

    /** 把已完成/失败的页合并进 manifest 并落盘。 */
    private fun persistProgress(gid: Long, results: Map<Int, DownloadPage>) {
        if (results.isEmpty()) return
        var merged: DownloadManifest? = null
        _tasks.update { list ->
            list.map { manifest ->
                if (manifest.gid != gid) manifest
                else {
                    manifest.copy(
                        pages = manifest.pages.map { results[it.index] ?: it },
                    ).also { merged = it }
                }
            }
        }
        merged?.let { DownloadStore.writeManifest(it) }
    }

    private fun replaceManifest(gid: Long, manifest: DownloadManifest) {
        _tasks.update { list -> list.map { if (it.gid == gid) manifest else it } }
        DownloadStore.writeManifest(manifest)
    }

    private fun updateManifest(gid: Long, transform: (DownloadManifest) -> DownloadManifest) {
        var updated: DownloadManifest? = null
        _tasks.update { list ->
            list.map { manifest ->
                if (manifest.gid == gid) transform(manifest).also { updated = it } else manifest
            }
        }
        updated?.let { DownloadStore.writeManifest(it) }
    }
}
/** 把失败页重置为待下载（重试用）。 */
internal fun resetFailedPages(manifest: DownloadManifest): DownloadManifest =
    manifest.copy(
        pages = manifest.pages.map {
            if (it.status == PageStatus.FAILED) it.copy(status = PageStatus.PENDING, attempts = 0, error = null) else it
        },
    )
