// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

import app.eros.shared.media.readPageList
import app.eros.shared.media.writePageList
import app.eros.shared.settings.AppSettings
import com.hippo.ehviewer.client.PageBatchHarvester
import com.hippo.ehviewer.net.EhCalls
import com.hippo.ehviewer.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 阅读器页地址的「按需批次」访问入口。
 *
 * E-Hentai 详情页缩略图分页是每 20 页一批（`?p=N` 覆盖第 20N+1..20N+20 页），
 * 所以拖到任意页都能只抓它所在的那一批，不必预先抓全量。
 *
 * 抓取引擎（批大小自适应、错位自愈、重试、同画廊串行限流）在 core 的
 * [PageBatchHarvester] 里；这里只负责接上应用层的磁盘缓存、IO 调度与后台预抓。
 */
object PageBatches {

    private const val TAG = "PageBatches"

    private const val MILLIS_PER_DAY = 24L * 3600_000L

    /** 预抓并发上限：同时最多 2 个预抓任务。 */
    private const val PREFETCH_CONCURRENCY = 2

    /** 预抓最多排队数：超过则丢弃，避免快速翻页堆积任务。 */
    private const val MAX_PREFETCH_QUEUE = 16

    private val harvester = PageBatchHarvester(
        object : PageBatchHarvester.Storage {

            override fun read(gid: Long): PageBatchHarvester.Stored? {
                val cached = readPageList(gid, ttlMillis()) ?: return null
                if (cached.urls.isEmpty()) return null
                return PageBatchHarvester.Stored(cached.urls, cached.totalPages)
            }

            override fun write(gid: Long, totalPages: Int, urls: List<String>) {
                // totalPages 用详情页声明的总页数，而不是本次抓到的条数：
                // 传 urls.size 会让「是否已抓齐」永远判假。
                writePageList(gid, totalPages, urls)
            }
        },
    )

    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefetchPermits = Semaphore(PREFETCH_CONCURRENCY)

    private val stateLock = Mutex()

    private val prefetchInflight = mutableSetOf<String>()

    private var prefetchPending = 0

    private fun ttlMillis(): Long = AppSettings.pageListTtlDays.toLong() * MILLIS_PER_DAY

    /** 已缓存的「页号(0-based) → /s/ 地址」。没有缓存返回空。 */
    suspend fun cached(gid: Long): Map<Int, String> = withContext(Dispatchers.IO) {
        val cached = readPageList(gid, ttlMillis()) ?: return@withContext emptyMap()
        PageBatchHarvester.indexPages(cached.urls)
    }

    /** 抓取包含 [pageIndex] 的那一批，返回该批的「页号 → 地址」。 */
    suspend fun fetch(
        gid: Long,
        token: String,
        pageIndex: Int,
        totalPages: Int,
    ): Map<Int, String> = onIoBlockingPaging { handle ->
        harvester.fetchBatch(gid, token, pageIndex, totalPages, handle)
    }

    /**
     * 把阻塞的抓取跑在 IO 线程上，并把「协程被取消」翻译成「OkHttp Call 被 cancel」。
     *
     * EhEngine 是同步 API（`Call.execute()`），只置位 Job 中断不了 socket 读；
     * 必须靠 [EhCalls.cancel] 才能让 execute 立刻抛 `IOException("Canceled")`。
     * 这里用看门狗协程而不是 `invokeOnCompletion`：取消后 Job 会卡在 cancelling 态，
     * 完成回调迟迟不来，看门狗捕获 [CancellationException] 才是确定会发生的。
     */
    private suspend fun <T> onIoBlockingPaging(block: (EhCalls.Handle) -> T): T = withContext(Dispatchers.IO) {
        val handle = EhCalls.newHandle()
        val watchdog = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                EhCalls.cancel(handle)
                throw e
            }
        }
        try {
            block(handle)
        } finally {
            watchdog.cancel()
            EhCalls.detach(handle)
        }
    }

    /**
     * 后台预抓（不等待），用于顺序阅读时提前准备后面的页。
     *
     * - 有界：最多 [PREFETCH_CONCURRENCY] 个并发，排队超过 [MAX_PREFETCH_QUEUE] 直接丢弃
     * - 去重：同一 `gid + 页号` 只排一次
     */
    fun prefetch(gid: Long, token: String, pageIndex: Int, totalPages: Int) {
        if (pageIndex < 0 || (totalPages > 0 && pageIndex >= totalPages)) return
        val key = "$gid#$pageIndex"
        prefetchScope.launch {
            val accepted = stateLock.withLock {
                when {
                    !prefetchInflight.add(key) -> false
                    prefetchPending >= MAX_PREFETCH_QUEUE -> {
                        prefetchInflight.remove(key)
                        false
                    }
                    else -> {
                        prefetchPending++
                        true
                    }
                }
            }
            if (!accepted) return@launch
            try {
                prefetchPermits.withPermit {
                    runCatching { fetch(gid, token, pageIndex, totalPages) }
                        .onFailure { AppLog.w(TAG, "预抓第 ${pageIndex + 1} 页失败：${it.message}") }
                }
            } finally {
                stateLock.withLock {
                    prefetchInflight.remove(key)
                    prefetchPending--
                }
            }
        }
    }
}
