// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client

import com.hippo.ehviewer.net.EhCalls
import com.hippo.ehviewer.util.AppLog
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * 详情页缩略图分页的页地址抓取器。
 *
 * E-Hentai 的详情页每页放若干张缩略图（通常是 20 张，视缩略图质量而定），
 * 所以阅读第 N 页只需要抓它所在的那一批，不必先把整本抓完。这里负责：
 *
 *  1. **批大小自适应**：默认按 20 张一批换算；出现错位时用 `?p=0` 与 `?p=1`
 *     首项页号差反推真实批大小。
 *  2. **索引错位自愈**：目标页不在抓回的批次里 → 重新探测批大小并重抓一次。
 *  3. **不自己重试**：重试（5xx / 429 / IO、退避与取消）由 HTTP 层的
 *     `RetryInterceptor` 统一负责。本类再叠一层会把一次 503 放大成 9 次请求，
 *     日志里「同一地址 GET 11–15 次」就有它一份。
 *  4. **串行限流**：同一画廊会话的请求用一把锁串起来（间隔 150ms），
 *     避免并发被站点限速时互相拖慢（实测 3 个并发请求各要 7 秒）。
 *     锁挂在会话上，随 LRU 淘汰一起回收——旧实现按 gid 存锁且从不移除，
 *     看过的画廊越多、泄漏的锁越多。同一 gid 的不同 token 是两个会话，
 *     各有各的锁：它们本来就是两份不同的页地址。
 *  5. **会话缓存**：最近 8 个画廊的页地址留在内存里，按访问序淘汰。
 *
 * 落盘缓存由调用方通过 [Storage] 注入，core 不认识任何应用层设置。
 */
class PageBatchHarvester(private val storage: Storage) {

    /** 页地址的持久化接口，由上层实现（读写磁盘缓存）。 */
    interface Storage {

        /** 读缓存；没有或已过期返回 null。 */
        fun read(gid: Long): Stored?

        /**
         * 写缓存。
         *
         * [totalPages] 是详情页声明的总页数（不是本次抓到的条数）——
         * 写错会让「是否抓齐」的判断永远为假。
         */
        fun write(gid: Long, totalPages: Int, urls: List<String>)
    }

    /** 缓存里的一批页地址。 */
    class Stored(val urls: List<String>, val totalPages: Int)

    private class Session(val gid: Long, val token: String) {
        /**
         * 同会话串行锁：跟着会话一起被 LRU 淘汰，不需要额外的清理。
         *
         * 之前是 `ConcurrentHashMap<Long, ReentrantLock>`，`computeIfAbsent` 之后
         * 从不移除，锁的数量等于「这辈子看过的画廊数」。
         */
        val lock = ReentrantLock()

        @Volatile var urls: List<String> = emptyList()
        @Volatile var totalPages: Int = 0
        @Volatile var pagesPerBatch: Int = DEFAULT_PAGES_PER_BATCH
        @Volatile var stepProbed: Boolean = false
    }

    /** 同一批次只有一个线程真去抓，其它线程等结果。 */
    private class Pending {
        val latch = CountDownLatch(1)

        @Volatile var result: Map<Int, String>? = null

        @Volatile var error: Throwable? = null
    }

    /**
     * 网络抓取的注入点：默认走 [EhEngine]，测试里替换成内存实现。
     *
     * 这样会话隔离/去重/取消这些**本类自己的语义**可以脱离真实网络来验证；
     * 真实请求路径仍由 `EhEngine` 自己的测试覆盖。
     */
    internal var batchFetcher: (Long, String, Int, EhCalls.Handle?) -> List<String> =
        { gid, token, p, key -> EhEngine.galleryPagingBatch(gid, token, p, key) }

    internal var metaFetcher: (Long, String, EhCalls.Handle?) -> EhEngine.GalleryPaging =
        { gid, token, key -> EhEngine.galleryPagingMeta(gid, token, key) }

    private val sessionsLock = Any()

    /**
     * 会话表：访问序 LRU，只留最近 [MAX_SESSIONS] 个「画廊 + token」组合。
     *
     * 键里带 token 是必需的：同一 gid 换 token（上传者换版本、画廊被替换）时，
     * 旧会话里的页地址全部作废。只按 gid 存会让新 token 复用旧地址而抓不到图。
     */
    private val sessions = object : LinkedHashMap<String, Session>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Session>): Boolean =
            size > MAX_SESSIONS
    }

    private val inflight = ConcurrentHashMap<String, Pending>()

    // ------------------------------------------------------------------ 会话

    private fun sessionKey(gid: Long, token: String): String = "$gid#$token"

    private fun session(gid: Long, token: String): Session = synchronized(sessionsLock) {
        val key = sessionKey(gid, token)
        sessions[key] ?: Session(gid, token).also {
            loadCached(it)
            sessions[key] = it
        }
    }

    private fun loadCached(session: Session) {
        val stored = runCatching { storage.read(session.gid) }.getOrNull() ?: return
        if (stored.urls.isEmpty()) return
        session.urls = stored.urls
        session.totalPages = stored.totalPages
    }

    // ---------------------------------------------------------------- 批大小

    /**
     * 确认该画廊每批缩略图数量。
     *
     * 默认直接按 20 走、不探测：站点长期稳定是 20 张，而探测要白抓 `?p=0` 与
     * `?p=1` 两个页面（弱网约 1 秒）。只有 [force] 时才真的去探测。
     */
    private fun ensureStep(session: Session, force: Boolean = false, cancelKey: EhCalls.Handle? = null) {
        if (session.stepProbed && !force) return
        withSessionLock(session, cancelKey) {
            if (session.stepProbed && !force) return@withSessionLock
            if (!force) {
                session.pagesPerBatch = DEFAULT_PAGES_PER_BATCH
                session.stepProbed = true
                return@withSessionLock
            }
            val step = runCatching {
                val meta = metaFetcher(session.gid, session.token, cancelKey)
                val firstOf0 = meta.firstLinks.firstOrNull()?.let { pageIndex(it) }
                    ?: return@runCatching DEFAULT_PAGES_PER_BATCH
                val firstOf1 = batchFetcher(session.gid, session.token, 1, cancelKey)
                    .firstOrNull()?.let { pageIndex(it) }
                    ?: return@runCatching DEFAULT_PAGES_PER_BATCH
                (firstOf1 - firstOf0).takeIf { it in 1..200 } ?: DEFAULT_PAGES_PER_BATCH
            }.getOrDefault(DEFAULT_PAGES_PER_BATCH)
            AppLog.i(TAG, "探测批大小：gid=${session.gid} 每批 $step 张")
            session.pagesPerBatch = step
            session.stepProbed = true
        }
    }

    // ------------------------------------------------------------------ 抓取

    /**
     * 抓取包含第 [pageIndex] 页（0-based）的那一批，返回「页号 → 页地址」。
     *
     * 已在缓存里的页不会发请求，直接按当前批大小切一段返回。
     */
    fun fetchBatch(
        gid: Long,
        token: String,
        pageIndex: Int,
        totalPages: Int,
        cancelKey: EhCalls.Handle? = null,
    ): Map<Int, String> {
        throwIfCancelled(cancelKey)
        val session = session(gid, token)
        val known = indexPages(session.urls)
        if (known.containsKey(pageIndex)) {
            val step = if (session.stepProbed) session.pagesPerBatch else DEFAULT_PAGES_PER_BATCH
            val from = (pageIndex / step) * step
            AppLog.d(TAG, "${pageRangeText(from, step, session.totalPages)} 命中缓存（已知 ${known.size} 页）")
            return known.filterKeys { it in from until (from + step) }
        }
        ensureStep(session, cancelKey = cancelKey)
        var batch = pageIndex.coerceAtLeast(0) / session.pagesPerBatch
        var batchMap = fetchBatchInternal(session, batch, totalPages, cancelKey)

        if (!batchMap.containsKey(pageIndex)) {
            // 错位自愈：上传者增补了页，或站点换了缩略图质量，批大小变了。
            AppLog.w(TAG, "缺少第 ${pageIndex + 1} 页：batch=$batch 每批=${session.pagesPerBatch}，重新探测")
            ensureStep(session, force = true, cancelKey = cancelKey)
            val retryBatch = pageIndex.coerceAtLeast(0) / session.pagesPerBatch
            if (retryBatch != batch) {
                batch = retryBatch
                batchMap = fetchBatchInternal(session, batch, totalPages, cancelKey)
            }
            if (!batchMap.containsKey(pageIndex)) {
                throw IllegalStateException("页地址缺失：第 ${pageIndex + 1} 页")
            }
        }
        return batchMap
    }

    private fun fetchBatchInternal(
        session: Session,
        batch: Int,
        totalPages: Int,
        cancelKey: EhCalls.Handle?,
    ): Map<Int, String> {
        // 键里带 token：同一 gid 换了 token 时旧批次结果对新会话没有意义。
        val key = "${session.gid}#${session.token}#$batch"
        val mine = Pending()
        val existing = inflight.putIfAbsent(key, mine)
        if (existing != null) {
            awaitPending(existing, cancelKey)
            existing.error?.let { throw it }
            return existing.result.orEmpty()
        }
        try {
            val startedAt = System.nanoTime()
            val raw = withSessionLock(session, cancelKey) {
                // 同一会话串行：一个抓完再抓下一个，避免被限速拖成慢查询。
                sleepCancellable(REQUEST_GAP_MS, cancelKey)
                batchFetcher(session.gid, session.token, batch, cancelKey)
            }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            val batchMap = indexPages(normalize(raw))
            val merged = normalize(session.urls + raw)
            session.urls = merged
            session.totalPages = maxOf(session.totalPages, totalPages, merged.size)
            runCatching { storage.write(session.gid, session.totalPages, merged) }
            AppLog.i(
                TAG,
                "${pageRangeText(batch * session.pagesPerBatch, session.pagesPerBatch, totalPages)}：" +
                    "本批 ${batchMap.size} 条，累计 ${merged.size}/$totalPages，耗时 ${elapsedMs}ms",
            )
            mine.result = batchMap
            return batchMap
        } catch (t: Throwable) {
            mine.error = t
            throw t
        } finally {
            mine.latch.countDown()
            inflight.remove(key)
        }
    }

    // ------------------------------------------------------------------ 工具

    /** 「第 801–820 页」——日志里给页码区间，别给 0-based 的批号。 */
    private fun pageRangeText(fromIndex: Int, step: Int, totalPages: Int): String {
        val start = fromIndex + 1
        val end = fromIndex + step
        val capped = if (totalPages > 0) minOf(end, totalPages) else end
        return if (capped <= start) "第 $start 页" else "第 $start–$capped 页"
    }

    /**
     * 同会话串行锁；拿锁前后都检查取消，排队期间被取消就不白跑一次请求。
     *
     * 锁在 [Session] 上，会话被 LRU 淘汰时锁跟着回收，不会再随「看过的画廊数」增长。
     */
    private fun <T> withSessionLock(session: Session, cancelKey: EhCalls.Handle? = null, block: () -> T): T {
        throwIfCancelled(cancelKey)
        session.lock.lock()
        try {
            // 排队期间可能已被取消：拿到锁后先确认，避免白跑一次完整请求。
            throwIfCancelled(cancelKey)
            return block()
        } finally {
            session.lock.unlock()
        }
    }

    /** 取消即抛：把协程取消（[EhCalls.cancel]）翻译成调用方熟悉的 IO 异常。 */
    private fun throwIfCancelled(cancelKey: EhCalls.Handle?) {
        if (cancelKey != null && cancelKey.cancelled) {
            throw InterruptedIOException("Canceled")
        }
    }

    /**
     * 可取消的睡眠：分片轮询取消位，别一次 sleep 到底。
     *
     * 旧实现是 `Thread.sleep` + `runCatching` 吞 `InterruptedException` ——
     * 既拿不到取消信号，中断标记也被吃掉，取消后照样把剩下的活干完。
     */
    private fun sleepCancellable(millis: Long, cancelKey: EhCalls.Handle?) {
        if (millis <= 0) return
        var remaining = millis
        while (remaining > 0) {
            throwIfCancelled(cancelKey)
            val slice = minOf(remaining, CANCEL_POLL_MS)
            try {
                Thread.sleep(slice)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Canceled")
            }
            remaining -= slice
        }
    }

    /** 等同一个批次的别人抓完；自己这边可被取消，不会一直挂着。 */
    private fun awaitPending(pending: Pending, cancelKey: EhCalls.Handle?) {
        while (true) {
            throwIfCancelled(cancelKey)
            if (pending.latch.await(CANCEL_POLL_MS, TimeUnit.MILLISECONDS)) return
        }
    }

    private fun normalize(list: List<String>): List<String> = list
        .map { if (it.startsWith("http")) it else SITE_ROOT + it.trimStart('/') }
        .distinct()
        .sortedBy { pageIndex(it) }

    companion object {

        private const val TAG = "Harvest"

        private const val DEFAULT_PAGES_PER_BATCH = 20

        private const val REQUEST_GAP_MS = 150L

        /** 可取消等待的轮询粒度：取消最迟在一个粒度内生效。 */
        private const val CANCEL_POLL_MS = 40L

        private const val MAX_SESSIONS = 8

        private const val SITE_ROOT = "https://e-hentai.org/"

        /** 页号（0-based）：地址尾部 `-<n>` 减一；认不出时按第 1 页算。 */
        @JvmStatic
        fun pageIndex(url: String): Int =
            ((PAGE_NUMBER.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)

        /** 由地址列表构造「页号 → 地址」。 */
        @JvmStatic
        fun indexPages(urls: List<String>): Map<Int, String> =
            urls.mapNotNull { url -> pageIndex(url).let { it to url } }.toMap()

        private val PAGE_NUMBER = Regex("""-(\d+)(?:[/?#]|$)""")
    }
}
