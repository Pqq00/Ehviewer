// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client

import com.hippo.ehviewer.util.AppLog
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
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
 *  3. **重试**：IO 类异常 / 超时 / HTTP 5xx / 429 重试 3 次，线性退避。
 *  4. **串行限流**：同一画廊的请求用一把锁串起来（间隔 150ms），
 *     避免并发被站点限速时互相拖慢（实测 3 个并发请求各要 7 秒）。
 *  5. **会话缓存**：最近 8 个画廊的页地址留在内存里，按访问序淘汰。
 *
 * 落盘缓存由调用方通过 [Storage] 注入，core 不认识任何应用层设置。
 */
class PageBatchHarvester(private val storage: Storage) {

    /** 页地址的持久化接口，由上层实现（读写磁盘缓存）。 */
    interface Storage {

        /** 读缓存；没有或已过期返回 null。 */
        fun read(gid: Long): Stored?

        /** 写缓存。 */
        fun write(gid: Long, urls: List<String>)
    }

    /** 缓存里的一批页地址。 */
    class Stored(val urls: List<String>, val totalPages: Int)

    private class Session(val gid: Long, val token: String) {
        @Volatile var urls: List<String> = emptyList()
        @Volatile var totalPages: Int = 0
        @Volatile var pagesPerBatch: Int = DEFAULT_PAGES_PER_BATCH
        @Volatile var stepProbed: Boolean = false
        @Volatile var done: Boolean = false
    }

    /** 同一批次只有一个线程真去抓，其它线程等结果。 */
    private class Pending {
        val latch = CountDownLatch(1)

        @Volatile var result: Map<Int, String>? = null

        @Volatile var error: Throwable? = null
    }

    private val sessionsLock = Any()

    /** 会话表：访问序 LRU，只留最近 [MAX_SESSIONS] 个画廊。 */
    private val sessions = object : LinkedHashMap<Long, Session>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Session>): Boolean =
            size > MAX_SESSIONS
    }

    private val gidLocks = ConcurrentHashMap<Long, ReentrantLock>()

    private val inflight = ConcurrentHashMap<String, Pending>()

    // ------------------------------------------------------------------ 会话

    private fun session(gid: Long, token: String): Session = synchronized(sessionsLock) {
        sessions[gid] ?: Session(gid, token).also {
            loadCached(it)
            sessions[gid] = it
        }
    }

    private fun loadCached(session: Session) {
        val stored = runCatching { storage.read(session.gid) }.getOrNull() ?: return
        if (stored.urls.isEmpty()) return
        session.urls = stored.urls
        session.totalPages = stored.totalPages
        session.done = stored.urls.size >= stored.totalPages
    }

    // ---------------------------------------------------------------- 批大小

    /**
     * 确认该画廊每批缩略图数量。
     *
     * 默认直接按 20 走、不探测：站点长期稳定是 20 张，而探测要白抓 `?p=0` 与
     * `?p=1` 两个页面（弱网约 1 秒）。只有 [force] 时才真的去探测。
     */
    private fun ensureStep(session: Session, force: Boolean = false) {
        if (session.stepProbed && !force) return
        withGidLock(session.gid) {
            if (session.stepProbed && !force) return@withGidLock
            if (!force) {
                session.pagesPerBatch = DEFAULT_PAGES_PER_BATCH
                session.stepProbed = true
                return@withGidLock
            }
            val step = runCatching {
                val meta = retryIo { EhEngine.galleryPagingMeta(session.gid, session.token) }
                val firstOf0 = meta.firstLinks.firstOrNull()?.let { pageIndex(it) }
                    ?: return@runCatching DEFAULT_PAGES_PER_BATCH
                val firstOf1 = retryIo { EhEngine.galleryPagingBatch(session.gid, session.token, 1) }
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
    fun fetchBatch(gid: Long, token: String, pageIndex: Int, totalPages: Int): Map<Int, String> {
        val session = session(gid, token)
        val known = indexPages(session.urls)
        if (known.containsKey(pageIndex)) {
            val step = if (session.stepProbed) session.pagesPerBatch else DEFAULT_PAGES_PER_BATCH
            val from = (pageIndex / step) * step
            AppLog.d(TAG, "${pageRangeText(from, step, session.totalPages)} 命中缓存（已知 ${known.size} 页）")
            return known.filterKeys { it in from until (from + step) }
        }
        ensureStep(session)
        var batch = pageIndex.coerceAtLeast(0) / session.pagesPerBatch
        var batchMap = fetchBatchInternal(session, batch, totalPages)

        if (!batchMap.containsKey(pageIndex)) {
            // 错位自愈：上传者增补了页，或站点换了缩略图质量，批大小变了。
            AppLog.w(TAG, "缺少第 ${pageIndex + 1} 页：batch=$batch 每批=${session.pagesPerBatch}，重新探测")
            ensureStep(session, force = true)
            val retryBatch = pageIndex.coerceAtLeast(0) / session.pagesPerBatch
            if (retryBatch != batch) {
                batch = retryBatch
                batchMap = fetchBatchInternal(session, batch, totalPages)
            }
            if (!batchMap.containsKey(pageIndex)) {
                throw IllegalStateException("页地址缺失：第 ${pageIndex + 1} 页")
            }
        }
        return batchMap
    }

    private fun fetchBatchInternal(session: Session, batch: Int, totalPages: Int): Map<Int, String> {
        val key = "${session.gid}#$batch"
        val mine = Pending()
        val existing = inflight.putIfAbsent(key, mine)
        if (existing != null) {
            existing.latch.await()
            existing.error?.let { throw it }
            return existing.result.orEmpty()
        }
        try {
            val startedAt = System.nanoTime()
            val raw = withGidLock(session.gid) {
                // 同一画廊串行：一个抓完再抓下一个，避免被限速拖成慢查询。
                runCatching { Thread.sleep(REQUEST_GAP_MS) }
                retryIo { EhEngine.galleryPagingBatch(session.gid, session.token, batch) }
            }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            val batchMap = indexPages(normalize(raw))
            val merged = normalize(session.urls + raw)
            session.urls = merged
            session.totalPages = maxOf(session.totalPages, totalPages, merged.size)
            runCatching { storage.write(session.gid, merged) }
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

    private fun <T> withGidLock(gid: Long, block: () -> T): T {
        val lock = gidLocks.computeIfAbsent(gid) { ReentrantLock() }
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    /** 网络重试：只重试 IO 类异常，第 n 次失败后退避 500ms × n。 */
    private fun <T> retryIo(block: () -> T): T {
        var last: Throwable? = null
        for (attempt in 0 until RETRY_TIMES) {
            try {
                return block()
            } catch (t: Throwable) {
                last = t
                val retryable = t is IOException ||
                    t.message?.contains("timeout", ignoreCase = true) == true ||
                    t.message?.let { m -> m.contains("HTTP") && (m.contains("50") || m.contains("429")) } == true
                if (!retryable || attempt == RETRY_TIMES - 1) throw t
                AppLog.w(TAG, "第 ${attempt + 1}/$RETRY_TIMES 次失败，重试：${t.message}")
                runCatching { Thread.sleep(500L * (attempt + 1)) }
            }
        }
        throw last ?: IllegalStateException("重试失败")
    }

    private fun normalize(list: List<String>): List<String> = list
        .map { if (it.startsWith("http")) it else SITE_ROOT + it.trimStart('/') }
        .distinct()
        .sortedBy { pageIndex(it) }

    companion object {

        private const val TAG = "Harvest"

        private const val DEFAULT_PAGES_PER_BATCH = 20

        private const val RETRY_TIMES = 3

        private const val REQUEST_GAP_MS = 150L

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
