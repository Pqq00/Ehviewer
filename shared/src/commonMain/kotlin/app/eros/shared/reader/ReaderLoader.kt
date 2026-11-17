// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import app.eros.shared.data.EhApi
import app.eros.shared.data.PageBatches
import app.eros.shared.data.ReaderImageUi
import app.eros.shared.data.cache.DetailMemoryCache
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.data.cache.ImageUrlRecord
import app.eros.shared.data.createEhApi
import app.eros.shared.data.toEhError
import app.eros.shared.data.userMessage
import app.eros.shared.media.decodeImageBitmap
import app.eros.shared.media.readPageCache
import app.eros.shared.media.saveImage
import app.eros.shared.media.writePageCache
import app.eros.shared.encodeJpeg
import app.eros.shared.shareImage
import app.eros.shared.settings.AppSettings
import com.hippo.ehviewer.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/**
 * 阅读器纯 IO 层：页地址解析、图片下载、解码、位图缓存、预取调度。
 *
 * 这里**不持有**页码、模式、缩放等交互状态（那些在 [ReaderSession]），
 * 只对外暴露 [pageImages] / [urlsByIndex] / [loading] / [error] 供 UI 渲染。
 */
class ReaderLoader(
    val gid: Long,
    val token: String,
    private val galleryPages: Int = 0,
    private val api: EhApi = createEhApi(),
) {

    private val source = api
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 已抓到的「页号(0-based) → /s/ 地址」，按批次惰性填充。 */
    var urlsByIndex by mutableStateOf<Map<Int, String>>(emptyMap())
        private set

    private val loadingBatchStarts = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    private companion object {
        /** 日志 tag：导出诊断包后 `[Reader]` 开头的行一次 grep 就能捞全。 */
        const val TAG = "Reader"

        /** E-H 详情页缩略图每批 20 页。 */
        const val PAGES_PER_BATCH = 20

        /** 后台预取的图片并发数（交互页另外占一个槽，永远不打排队）。 */
        const val PREFETCH_CONCURRENCY = 3

        /** 单张预取超过这个耗时就算「慢链路」，预取退化成串行。 */
        const val SLOW_LINK_THRESHOLD_MS = 4_000L

        /** 地址解析（`/s/` 页面）的并发数：请求很小，可以和图片下载并行跑。 */
        const val ADDRESS_CONCURRENCY = 6

        /** 位图缓存至少保留这么多页，预算再小也不要退化成 0 缓存。 */
        const val BITMAP_CACHE_MIN = 6

        /** 估算单页位图占用：ARGB_8888 每像素 4 字节。 */
        const val BYTES_PER_PIXEL = 4
    }

    /** index -> 图片加载状态 */
    var pageImages by mutableStateOf<Map<Int, PageImage>>(emptyMap())
        private set

    var loading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** 总页数：详情页给的真实页数优先。 */
    val totalPages: Int get() = maxOf(galleryPages, 1)

    /** 解码采样用的屏幕宽度（由 UI 在拿到窗口尺寸后写入）。 */
    var screenWidthPx by mutableIntStateOf(0)

    /** index -> 宽高比（来自详情页缩略图，未加载时可先占位）。 */
    var pageRatios: Map<Int, Float> by mutableStateOf(emptyMap())
        private set

    private var loadedOnce = false
    // 下面几张表会被主线程（onPageChanged → cancelOutside）与 IO 协程（任务结束时 remove）同时改，
    // 必须用并发容器：普通 LinkedHashMap 在「翻页取消」与「任务收尾」撞上时
    // 会抛 ConcurrentModificationException（发行版 logcat 已多次复现）。
    private val resolving = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    /** 每页正在跑的解析/下载任务，跳页后可以取消掉不再需要的那些。 */
    private val jobs = java.util.concurrent.ConcurrentHashMap<Int, Job>()

    /** 交互槽：只给「正在看的那一页」，保证当前页不会被后台预取挤占带宽。 */
    private val interactionPermit = Semaphore(1)

    /** 预取槽：后台预加载最多 [PREFETCH_CONCURRENCY] 张并发。 */
    private val prefetchPermit = Semaphore(PREFETCH_CONCURRENCY)

    /** 上一张预取图片耗时是否超过阈值；慢链路上把预取压成串行。 */
    private var linkSlow = false

    /** 慢链路时的预取串行闸门。 */
    private val slowPrefetchGate = Mutex()

    /** 地址解析的独立小池：5KB 的 /s/ 页面不该占用图片下载槽。 */
    private val addressPermit = Semaphore(ADDRESS_CONCURRENCY)

    /** index -> 原图地址（内存缓存，省掉 DB 查询）。 */
    private val addresses = java.util.concurrent.ConcurrentHashMap<Int, String>()

    /** index -> 正在解析地址的任务，避免同一页并发请求两次 /s/。 */
    private val addressJobs = java.util.concurrent.ConcurrentHashMap<Int, Deferred<String?>>()

    /**
     * 已解码位图的 LRU，按**字节预算**逐出（`pageCacheLimitMb`）。
     *
     * 位图很占内存（1200×1700 的 ARGB 约 8 MB），所以不随 [pageImages] 无限增长；
     * 但翻回去看上一页时也值得留着，省掉一次解码（以及页缓存被清后的重新下载）。
     */
    private val bitmapLru = object : LinkedHashMap<Int, ImageBitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>): Boolean {
            if (size <= BITMAP_CACHE_MIN) return false
            return bitmapBytes() > bitmapBudgetBytes()
        }
    }

    private fun bitmapBudgetBytes(): Long =
        AppSettings.pageCacheLimitMb.coerceIn(100, 2_000).toLong() * 1024L * 1024L

    private fun bitmapBytes(): Long {
        var sum = 0L
        for (bitmap in bitmapLru.values) {
            sum += bitmap.width.toLong() * bitmap.height.toLong() * BYTES_PER_PIXEL
        }
        return sum
    }

    /** 页面宽高比，用于未加载时占位，避免布局跳动。 */
    fun pageRatio(index: Int): Float {
        pageRatios[index]?.let { if (it > 0.01f) return it }
        bitmapLruGet(index)?.let { bmp ->
            if (bmp.height > 0) return bmp.width.toFloat() / bmp.height
        }
        return 3f / 4f
    }

    // ------------------------------------------------------------------ 装载

    /** 首次进入阅读器：恢复地址缓存 + 拉第一批。 */
    fun ensureLoaded(currentPage: Int) {
        if (loadedOnce) return
        loadedOnce = true
        scope.launch {
            loading = true
            error = null
            try {
                val cachedUrls = PageBatches.cached(gid)
                val previews = (DetailMemoryCache.get(gid, token) ?: GalleryCache.detail(gid, token))
                    ?.previews
                    .orEmpty()
                val seeded = LinkedHashMap<Int, String>(cachedUrls)
                previews.forEachIndexed { index, p -> seeded[index] = p.pageUrl }
                urlsByIndex = seeded
                pageRatios = previews.mapIndexedNotNull { index, p ->
                    val w = p.clipWidth
                    val h = p.clipHeight
                    if (w > 0 && h > 0) index to (w.toFloat() / h) else null
                }.toMap()
                AppLog.i(
                    TAG,
                    "cached urls=${cachedUrls.size} preview=${previews.size} " +
                        "seeded=${seeded.size} total=$totalPages",
                )
                ensureBatchFor(currentPage)
                prefetch(currentPage)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                error = t.toEhError().userMessage()
                AppLog.e(TAG, "初始化失败：$error")
            } finally {
                loading = false
            }
        }
    }

    /** 请求某一页（当前页）。 */
    fun requestPage(index: Int) {
        resolve(index, interactive = true)
    }

    /** 跳页/翻页后，重新安排这一页附近的任务。 */
    fun onPageChanged(index: Int) {
        val clamped = index.coerceIn(0, totalPages - 1)
        AppLog.d(TAG, "page -> $clamped")
        ensureBatchFor(clamped)
        prefetch(clamped)
        cancelOutside(keepWindow(clamped))
        evictFar(clamped)
    }

    /** 失败重试：先把这一页的状态清回 Pending，再重新解析。 */
    fun retry(index: Int) {
        if (index !in 0 until totalPages) return
        jobs.remove(index)?.cancel()
        resolving -= index
        pageImages = pageImages - index
        addresses.remove(index)
        resolve(index, interactive = true)
    }

    /** 保存当前页到相册；返回给 UI 显示的文案。 */
    suspend fun save(index: Int): String = withContext(Dispatchers.Default) {
        val url = urlsByIndex[index] ?: return@withContext "页地址还没加载好"
        runCatching {
            val page = source.readerPage(url)
            val bytes = withContext(Dispatchers.IO) {
                com.hippo.ehviewer.client.EhEngine.downloadImage(page.imageUrl)
            }
            saveImage(bytes, "eros_${gid}_${index + 1}.jpg")
        }.fold(
            onSuccess = { "已保存：$it" },
            onFailure = { "保存失败：${it.toEhError().userMessage()}" },
        )
    }

    /**
     * 把某一页的原图交给系统分享；返回给 UI 显示的文案。
     *
     * 取图顺序（前三级都不走网络，就是「发送已经加载好的图片」）：
     * 1. 阅读器的页面字节缓存（原始 JPEG，由 [loadPage] 落盘）；
     * 2. 已下载画廊的本地文件（同样是原始 JPEG）；
     * 3. 内存里的已解码位图，就地重编码为 JPEG（质量 95，轻微损失）；
     * 4. 上面都没有才下载一次。
     */
    suspend fun share(index: Int): String = withContext(Dispatchers.Default) {
        if (index !in 0 until totalPages) return@withContext "页数超出范围"
        val fileName = "eros_${gid}_${index + 1}.jpg"
        val cached = readPageCache(gid, index)?.takeIf { it.isNotEmpty() }
        val local = cached ?: runCatching {
            app.eros.shared.download.DownloadManager.localPage(gid, index)?.takeIf { it.isNotEmpty() }
        }.getOrNull()
        if (local != null) return@withContext shareImage(local, fileName)
        val bitmap = (pageImages[index] as? PageImage.Ready)?.bitmap
        if (bitmap != null) {
            val bytes = encodeJpeg(bitmap)
            if (bytes != null) return@withContext shareImage(bytes, fileName)
        }
        val url = urlsByIndex[index] ?: return@withContext "页地址还没加载好"
        runCatching {
            val page = source.readerPage(url)
            val bytes = withContext(Dispatchers.IO) {
                com.hippo.ehviewer.client.EhEngine.downloadImage(page.imageUrl)
            }
            shareImage(bytes, fileName)
        }.fold(
            onSuccess = { it },
            onFailure = { "分享失败：${it.toEhError().userMessage()}" },
        )
    }

    // ------------------------------------------------------------- 预取与取消

    private fun keepWindow(index: Int): Set<Int> {
        val set = LinkedHashSet<Int>()
        set += index
        set += ReaderEngine.resolveWindow(index, totalPages)
        set += ReaderEngine.preloadWindow(
            page = index,
            totalPages = totalPages,
            ahead = AppSettings.readerPreloadPages.coerceIn(0, 10),
            behind = 1,
        )
        return set
    }

    private fun cancelOutside(keep: Set<Int>) {
        // 先取 key 快照，再逐个 remove(key)?.cancel()：并发容器上遍历时直接改是安全的，
        // 但「先 filterKeys 成一份副本再 remove」会漏掉遍历期间新插入的任务。
        val cancelledAddress = addressJobs.keys.filter { it !in keep }
        cancelledAddress.forEach { addressJobs.remove(it)?.cancel() }
        val cancelled = jobs.keys.filter { it !in keep }
        cancelled.forEach { jobs.remove(it)?.cancel() }
        if (cancelled.isNotEmpty() || cancelledAddress.isNotEmpty()) {
            AppLog.d(TAG, "preload cancel=${cancelled.size + cancelledAddress.size} keep=${keep.size}")
        }
        if (cancelled.isNotEmpty()) {
            var next = pageImages
            cancelled.forEach { idx ->
                if (next[idx] !is PageImage.Ready) next = next - idx
            }
            pageImages = next
        }
    }

    private fun evictFar(index: Int) {
        val keep = keepWindow(index)
        val kept = pageImages.filterKeys { it in keep }
        if (kept.size != pageImages.size) pageImages = kept
    }

    private fun ensureBatchFor(index: Int, warmCount: Int = 0) {
        if (index !in 0 until totalPages) return
        if (urlsByIndex[index] != null) return
        val start = batchStart(index)
        if (!loadingBatchStarts.add(start)) return
        if (pageImages[index] == null) {
            pageImages = pageImages + (index to PageImage.ResolvingAddress)
        }
        scope.launch {
            val started = TimeSource.Monotonic.markNow()
            try {
                val map = PageBatches.fetch(gid, token, index, totalPages)
                if (map.isEmpty()) {
                    failWindow(index, "页地址为空")
                    return@launch
                }
                urlsByIndex = LinkedHashMap(urlsByIndex).apply { putAll(map) }
                AppLog.i(
                    TAG,
                    "page $index resolved +${map.size} total=${urlsByIndex.size} " +
                        "cost=${started.elapsedNow().inWholeMilliseconds}ms",
                )
                val warm = LinkedHashSet<Int>()
                warm += ReaderEngine.resolveWindow(index, totalPages)
                warm += ReaderEngine.batchWarmPages(index, totalPages, PAGES_PER_BATCH, warmCount)
                warm.forEach { resolve(it, interactive = it == index) }
                prefetch(index)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                failWindow(index, t.toEhError().userMessage())
                AppLog.w(TAG, "batch $start failed: ${t.message}")
            } finally {
                loadingBatchStarts.remove(start)
            }
        }
    }

    private fun failWindow(index: Int, message: String) {
        var next = pageImages
        ReaderEngine.resolveWindow(index, totalPages).forEach { idx ->
            if (next[idx] !is PageImage.Ready) next = next + (idx to PageImage.Failed(message))
        }
        pageImages = next
    }

    private fun batchStart(index: Int): Int = index.coerceAtLeast(0) / PAGES_PER_BATCH

    private fun prefetch(index: Int) {
        warmAddresses(index)
        val target = ReaderEngine.preloadTarget(index, AppSettings.readerPreloadPages, totalPages)
        if (target != null) {
            if (urlsByIndex[target] == null) {
                ensureBatchFor(target, warmCount = 2)
            } else {
                resolve(target)
            }
        }
        resolve(index, interactive = true)
        ReaderEngine.preloadWindow(index, totalPages, AppSettings.readerPreloadPages, 1).forEach { resolve(it) }
    }

    private fun warmAddresses(index: Int) {
        val window = LinkedHashSet<Int>()
        window += index
        window += ReaderEngine.preloadWindow(index, totalPages, AppSettings.readerPreloadPages, 1)
        window.forEach { idx ->
            val url = urlsByIndex[idx] ?: return@forEach
            if (pageImages[idx] is PageImage.Ready) return@forEach
            startAddressJob(idx, url)
        }
    }

    // --------------------------------------------------------------- 地址解析

    private suspend fun addressFor(index: Int, pageUrl: String): String? {
        addresses[index]?.let { return it }
        val record = runCatching { GalleryCache.imageUrl(gid, index) }.getOrNull()
        if (record != null && record.pageUrl == pageUrl) {
            addresses[index] = record.imageUrl
            return record.imageUrl
        }
        return startAddressJob(index, pageUrl).await()
    }

    private fun startAddressJob(index: Int, pageUrl: String): Deferred<String?> {
        // 已解析过的页直接复用结果。缺这一条时，warmAddresses 每翻一页都会把窗口内
        // 「图片尚未 Ready」的页重新解析一遍（HTTP 缓存 1ms 命中），实测一次阅读里
        // 同一页地址被重复解析十几次、白占 addressPermit 并刷爆日志。
        addresses[index]?.let { return CompletableDeferred(it) }
        addressJobs[index]?.let { return it }
        val job = scope.async {
            try {
                val started = TimeSource.Monotonic.markNow()
                val imageUrl = addressPermit.withPermit { source.readerPageUrl(pageUrl) }
                AppLog.d(TAG, "addr page=$index took=${started.elapsedNow().inWholeMilliseconds}ms")
                cacheAddress(index, pageUrl, imageUrl)
                imageUrl
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                AppLog.w(TAG, "addr page=$index failed: ${t.message}")
                null
            } finally {
                addressJobs.remove(index)
            }
        }
        addressJobs[index] = job
        return job
    }

    private suspend fun cacheAddress(index: Int, pageUrl: String, imageUrl: String) {
        addresses[index] = imageUrl
        runCatching {
            GalleryCache.saveImageUrl(ImageUrlRecord(gid, index, pageUrl, imageUrl))
        }
    }

    // --------------------------------------------------------------- 图片加载

    private fun resolve(index: Int, interactive: Boolean = false) {
        if (index !in 0 until totalPages) return
        if (pageImages[index] is PageImage.Ready) return
        if (index in resolving) return
        val pageUrl = urlsByIndex[index]
        if (pageUrl == null) {
            if (pageImages[index] == null) pageImages = pageImages + (index to PageImage.ResolvingAddress)
            ensureBatchFor(index)
            return
        }
        resolving += index
        if (!interactive) {
            AppLog.d(TAG, "preload page=$index queued live=${jobs.size}")
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                loadPage(index, pageUrl, interactive)
            } finally {
                resolving -= index
                jobs.remove(index)
            }
        }
        jobs[index] = job
        job.start()
    }

    private suspend fun loadPage(index: Int, pageUrl: String, interactive: Boolean) {
        val tag = if (interactive) "page=$index" else "preload page=$index"
        try {
            // 刚看过的页直接复用位图：不下载、不解码
            bitmapLruGet(index)?.let { bitmap ->
                publishReady(index, bitmap)
                AppLog.d(TAG, "$tag memory ${bitmap.width}x${bitmap.height}")
                return
            }
            // 已下载的画廊优先读本地文件（离线可看）
            val local = runCatching {
                app.eros.shared.download.DownloadManager.localPage(gid, index)
            }.getOrNull()
            if (local != null) {
                val bitmap = decodeImageBitmap(local, maxWidthPx = screenWidthPx)
                if (bitmap != null) publishReady(index, bitmap)
                else pageImages = pageImages + (index to PageImage.Failed("解码失败"))
                AppLog.d(TAG, "$tag local ${local.size}B")
                return
            }
            val cached = readPageCache(gid, index)
            val netStart = TimeSource.Monotonic.markNow()
            val bytes = if (cached != null) {
                AppLog.d(TAG, "$tag cached ${cached.size}B")
                cached
            } else {
                pageImages = pageImages + (index to PageImage.Loading(0f))
                // 地址（/s/ → 原图 URL）在下载许可之外解析：它是 5KB 的小请求，
                // 不该占着下载槽，也不该被别的大图堵住。
                val address = addressFor(index, pageUrl)
                // 地址已知就直接下原图（省掉一次 /s/ 解析）；失败说明地址过期（H@H key 变了），
                // 清掉缓存后交给 readerImage 走「解析 + 下载 + 限流补救」的整页流程。
                val known = address?.let { url ->
                    runCatching { downloadBytes(index, url, interactive) }
                        .onFailure {
                            runCatching { GalleryCache.deleteImageUrl(gid, index) }
                            addresses.remove(index)
                        }
                        .getOrNull()
                }
                val data = known ?: downloadReaderBytes(index, pageUrl, interactive)
                    .also { r -> cacheAddress(index, pageUrl, r.imageUrl) }
                    .bytes
                writePageCache(gid, index, data)
                data
            }
            val netMs = netStart.elapsedNow().inWholeMilliseconds
            val decodeStart = TimeSource.Monotonic.markNow()
            val bitmap = decodeImageBitmap(bytes, maxWidthPx = screenWidthPx)
            val decodeMs = decodeStart.elapsedNow().inWholeMilliseconds
            AppLog.i(TAG, "$tag timings net=${netMs}ms dec=${decodeMs}ms bytes=${bytes.size}")
            if (bitmap != null) publishReady(index, bitmap)
            else pageImages = pageImages + (index to PageImage.Failed("解码失败"))
        } catch (e: CancellationException) {
            AppLog.d(TAG, "$tag cancelled")
            if (pageImages[index] !is PageImage.Ready) pageImages = pageImages - index
            throw e
        } catch (t: Throwable) {
            // 缓存的图片地址可能已失效：清掉它，下次重试会重新解析 /s/ 页面
            runCatching { GalleryCache.deleteImageUrl(gid, index) }
            pageImages = pageImages + (index to PageImage.Failed(t.message ?: "加载失败"))
            AppLog.w(TAG, "$tag failed: ${t.message}")
        }
    }

    private fun publishReady(index: Int, bitmap: ImageBitmap) {
        synchronized(bitmapLru) { bitmapLru[index] = bitmap }
        pageImages = pageImages + (index to PageImage.Ready(bitmap))
    }

    private fun bitmapLruGet(index: Int): ImageBitmap? = synchronized(bitmapLru) { bitmapLru[index] }

    private suspend fun downloadBytes(index: Int, url: String, interactive: Boolean): ByteArray =
        withPermit(interactive) {
            source.imageBytes(url) { p ->
                pageImages = pageImages + (index to PageImage.Loading(p))
            }
        }

    /** 解析 `/s/` 页面并下载原图（含 H@H 限流补救），整段占一个下载许可。 */
    private suspend fun downloadReaderBytes(
        index: Int,
        pageUrl: String,
        interactive: Boolean,
    ): ReaderImageUi = withPermit(interactive) {
        source.readerImage(pageUrl) { p ->
            pageImages = pageImages + (index to PageImage.Loading(p))
        }
    }

    /**
     * 取一个下载许可执行 [block]，并按耗时自适应：
     *
     * - 交互页永远走独立的槽，不会被预取挤占；
     * - 预取槽最多 [PREFETCH_CONCURRENCY] 个，但链路很慢时（单页 > 4s）会退化成串行，
     *   避免几张同时下载把带宽摊薄、把当前页也拖慢（实测慢链路上并发反而更慢）。
     */
    private suspend fun <T> withPermit(interactive: Boolean, block: suspend () -> T): T {
        val permit = if (interactive) interactionPermit else prefetchPermit
        val started = TimeSource.Monotonic.markNow()
        val result = if (interactive || !linkSlow) {
            permit.withPermit { block() }
        } else {
            slowPrefetchGate.withLock { permit.withPermit { block() } }
        }
        if (!interactive) linkSlow = started.elapsedNow().inWholeMilliseconds > SLOW_LINK_THRESHOLD_MS
        return result
    }
}
