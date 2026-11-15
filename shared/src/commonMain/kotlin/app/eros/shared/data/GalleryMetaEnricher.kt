// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

import app.eros.shared.currentTimeMillis
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.data.cache.GalleryMetaRecord
import app.eros.shared.data.cache.TagRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 列表项元信息富化：用 gdata 批量补标签 / 评分 / 页数 / 发布时间。
 *
 * - 页面级原子富化：调用方等 [enrichPage] 返回后再发布列表，避免 chips 后到的布局跳变；
 * - 先查 [GalleryCache.loadMeta] 本地元数据；标签不受 TTL 限制（标签几乎不变，
 *   否则「标签屏蔽」会在 7 天后周期性失效），评分/页数/时间仍按 7 天；
 * - 本地能补齐的条目不再请求 gdata，并且会和网络结果一起放进 [EnrichResult.updated]，
 *   这样即使 gdata 失败，调用方也能拿到「本地已知标签」去过滤；
 * - 25 个/批，失败退避重试 3 次；
 * - 仍然失败的那批交给 [enrichRetryLoop] 在后台按 3s / 8s 自动补课，不再打扰用户。
 */
object GalleryMetaEnricher {

    private const val MAX_ENTRIES = 2000
    private const val BATCH = 25
    private const val META_TTL_MS = 7L * 24 * 60 * 60 * 1000

    /** 后台补课的退避节奏：前几次密一点，之后每分钟一次，直到补齐为止。 */
    private val AUTO_RETRY_DELAYS_MS = longArrayOf(3_000L, 8_000L, 20_000L, 45_000L)
    private const val AUTO_RETRY_INTERVAL_MS = 60_000L

    /** 正在后台补课的 gid：两次发布撞车时只补一次，避免重复 gdata 请求。 */
    private val retryLock = Mutex()
    private val retryInFlight = mutableSetOf<Long>()

    /**
     * 页面富化的默认超时。首屏是“等富化完成再发布”的，超时会给用户看到骨架屏。
     *
     * gdata 一批 25 项在正常网络约 0.5s、慢速 VPN 约 2.5s；1.5s 几乎必然超时，会退化成
     * “先出内容、chips 后到”，所以放宽到 3s。
     *
     * 注意：底层 gdata 是 OkHttp 阻塞调用，协程取消无法打断进行中的请求，
     * 因此这里只能算“尽力而为”的截止时间——真卡住时最多多等一次请求自身的超时。
     */
    const val PAGE_TIMEOUT_MS = 3000L

    data class EnrichResult(
        val updated: List<GalleryUi>,
        val failedGids: Set<Long>,
        val timedOut: Boolean,
        val requested: Int,
    )

    private val cache = object : LinkedHashMap<Long, GalleryUi>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, GalleryUi>?): Boolean =
            size > MAX_ENTRIES
    }

    private fun needsMeta(ui: GalleryUi): Boolean = ui.tags.isEmpty() || ui.rating <= 0f

    /** 把之前富化过的项合并进新列表（例如翻页回来）。 */
    fun merge(items: List<GalleryUi>): List<GalleryUi> {
        if (cache.isEmpty()) return items
        return items.map { cache[it.gid] ?: it }
    }

    fun cached(gid: Long): GalleryUi? = synchronized(cache) { cache[gid] }

    /**
     * 富化一页（或一批）列表项，返回可发布的最终结果。
     *
     * @param timeoutMs 页面级超时；超时后返回已完成的部分，并把剩余项计入 failedGids。
     */
    suspend fun enrichPage(
        api: EhApi,
        items: List<GalleryUi>,
        timeoutMs: Long = PAGE_TIMEOUT_MS,
    ): EnrichResult {
        if (items.isEmpty()) return EnrichResult(emptyList(), emptySet(), false, 0)
        merge(items).forEach { ui -> synchronized(cache) { cache[ui.gid] = ui } }

        // 1. 本地元数据回填：返回「已经是最终形态」的副本，调用方必须优先用它发布，
        //    否则 gdata 失败时列表里会是没标签的原对象，标签屏蔽就看不到标签了。
        val fromMeta = fillFromMeta(items)
        val resolved = if (fromMeta.isEmpty()) items else items.map { fromMeta[it.gid] ?: it }

        // 本地已知的条目不再请求网络（未评分的画廊 rating<=0 也照样算已知）。
        val pending = resolved.filter { needsMeta(it) && !fromMeta.containsKey(it.gid) }
            .distinctBy { it.gid }
        val out = LinkedHashMap<Long, GalleryUi>(fromMeta.size * 2)
        fromMeta.forEach { (gid, ui) -> out[gid] = ui }
        if (pending.isEmpty()) {
            println(
                "[Enrich] requested=0 meta=${fromMeta.size} ok=0 failed=0 " +
                    "timeout=false elapsed=0ms",
            )
            return EnrichResult(out.values.toList(), emptySet(), false, 0)
        }

        val now = currentTimeMillis()
        val done = HashSet<Long>(pending.size * 2)

        val completed = withTimeoutOrNull(timeoutMs) {
            pending.chunked(BATCH).forEach { chunk ->
                val got = fetchChunk(api, chunk)
                if (got.isNotEmpty()) {
                    got.forEach { ui ->
                        out[ui.gid] = ui
                        done.add(ui.gid)
                    }
                    saveMetadata(got, now)
                }
            }
        }

        val failed = pending.mapNotNull { it.gid }.filterNot { done.contains(it) }.toSet()
        synchronized(cache) { out.values.forEach { cache[it.gid] = it } }
        println(
            "[Enrich] requested=${pending.size} meta=${fromMeta.size} ok=${done.size} " +
                "failed=${failed.size} timeout=${completed == null} " +
                "elapsed=${currentTimeMillis() - now}ms",
        )
        return EnrichResult(out.values.toList(), failed, completed == null, pending.size)
    }

    /** 只重试指定 gid（后台补课 / 手动刷新都走这里）。 */
    suspend fun retry(
        api: EhApi,
        items: List<GalleryUi>,
        gids: Set<Long>,
        timeoutMs: Long = PAGE_TIMEOUT_MS,
    ): EnrichResult {
        val subset = items.filter { gids.contains(it.gid) }
        return enrichPage(api, subset, timeoutMs)
    }

    /**
     * 后台自动补课：把同一批失败 gid 一直重试下去，直到补齐或页面不再需要。
     *
     * 节奏是 [AUTO_RETRY_DELAYS_MS]（3s / 8s / 20s / 45s），之后按
     * [AUTO_RETRY_INTERVAL_MS]（60s）无限重试——弱网/断网时骨架会一直等到网络恢复。
     * 失败没有任何 UI；成功后通过 [onUpdated] 交回调用方（合进列表 + 落盘）。
     *
     * 三种情况下停止：全部补齐、[active] 返回 false（分类/周期已被切走）、
     * 作用域取消（状态对象销毁）。
     */
    fun CoroutineScope.enrichRetryLoop(
        api: EhApi,
        items: List<GalleryUi>,
        failedGids: Set<Long>,
        active: () -> Boolean = { true },
        onUpdated: suspend (List<GalleryUi>) -> Unit,
    ): Job = launch {
        if (failedGids.isEmpty() || items.isEmpty()) return@launch
        // 同一批 gid 只允许一条补课链在跑（首屏/加载更多可能同时失败）。
        val claimed = retryLock.withLock {
            val pending = failedGids - retryInFlight
            if (pending.isEmpty()) {
                null
            } else {
                retryInFlight += pending
                pending
            }
        } ?: return@launch
        try {
            var remaining = claimed
            var round = 0
            while (remaining.isNotEmpty()) {
                if (!active()) return@launch
                delay(AUTO_RETRY_DELAYS_MS.getOrNull(round) ?: AUTO_RETRY_INTERVAL_MS)
                round++
                if (!active()) return@launch
                val subset = items.filter { remaining.contains(it.gid) }
                if (subset.isEmpty()) return@launch
                val updated = retry(api, subset, remaining).updated
                if (updated.isEmpty()) continue
                val done = updated.map { it.gid }.toSet()
                retryLock.withLock { retryInFlight -= done }
                println("[Enrich] auto-retry ok=${done.size} remaining=${(remaining - done).size}")
                onUpdated(updated)
                remaining = remaining - done
            }
        } finally {
            retryLock.withLock { retryInFlight -= claimed }
        }
    }

    /**
     * 列表已经是完整元信息（例如 rankList(enrichTags = true)）时，
     * 直接写本地 meta + 标签表，后续列表命中即可零网络回填。
     */
    suspend fun remember(items: List<GalleryUi>) {
        if (items.isEmpty()) return
        merge(items)
        saveMetadata(items, currentTimeMillis())
    }

    /** 把列表里已有的标签批量落库（不发起网络请求）。 */
    suspend fun persistTags(items: List<GalleryUi>) {
        if (items.isEmpty()) return
        val now = currentTimeMillis()
        val records = items.flatMap { ui -> ui.tags.mapNotNull { tagRecord(it, now) } }
        if (records.isEmpty()) return
        runCatching { GalleryCache.saveTags(records) }
            .onFailure { println("[Enrich] persistTags failed: $it") }
    }

    /**
     * 用本地 meta 回填标签/评分/页数，返回「已经可以直接发布」的副本（key = gid）。
     *
     * 标签不设 TTL：标签几乎不会变，而过期就丢会让标签屏蔽在 7 天后失效；
     * 评分/页数/发布时间仍按 [META_TTL_MS] 判断新鲜度。
     */
    private suspend fun fillFromMeta(items: List<GalleryUi>): Map<Long, GalleryUi> {
        if (items.isEmpty()) return emptyMap()
        val missing = items.filter { needsMeta(it) }.map { it.gid }
        if (missing.isEmpty()) return emptyMap()
        val metas = runCatching { GalleryCache.loadMeta(missing) }.getOrDefault(emptyMap())
        if (metas.isEmpty()) return emptyMap()
        val now = currentTimeMillis()
        val out = LinkedHashMap<Long, GalleryUi>(metas.size * 2)
        items.forEach { ui ->
            val meta = metas[ui.gid] ?: return@forEach
            val fresh = now - meta.fetchedAt <= META_TTL_MS
            // 过期而且连标签都没存过：这条 meta 没有利用价值，让它走网络。
            if (!fresh && meta.tags.isEmpty()) return@forEach
            val merged = ui.copy(
                tags = if (ui.tags.isEmpty() && meta.tags.isNotEmpty()) meta.tags else ui.tags,
                rating = if (fresh && meta.rating > 0f) meta.rating else ui.rating,
                pages = if (fresh && meta.pages > 0) meta.pages else ui.pages,
                posted = ui.posted ?: if (fresh) meta.posted else null,
            )
            synchronized(cache) { cache[ui.gid] = merged }
            out[ui.gid] = merged
        }
        return out
    }

    private suspend fun fetchChunk(api: EhApi, chunk: List<GalleryUi>): List<GalleryUi> {
        var last: List<GalleryUi> = emptyList()
        repeat(3) { attempt ->
            val got = runCatching { api.enrichGalleries(chunk) }.getOrDefault(emptyList())
            if (got.isNotEmpty()) {
                last = got
                val missing = chunk.filter { ui ->
                    got.none { it.gid == ui.gid } && ui.tags.isEmpty()
                }
                if (missing.isEmpty()) return got
                // 缺项单独补一次
                val extra = runCatching { api.enrichGalleries(missing) }.getOrDefault(emptyList())
                if (extra.isNotEmpty()) return got + extra
            }
            if (attempt < 2) delay(500L shl attempt)
        }
        return last
    }

    private suspend fun saveMetadata(items: List<GalleryUi>, now: Long) {
        val records = items.map { ui ->
            GalleryMetaRecord(
                gid = ui.gid,
                tags = ui.tags,
                rating = ui.rating,
                pages = ui.pages,
                posted = ui.posted,
                fetchedAt = now,
            )
        }
        runCatching { GalleryCache.saveMeta(records) }
            .onFailure { println("[Enrich] saveMeta failed: $it") }
        persistTags(items)
    }

    private fun tagRecord(raw: String, now: Long): TagRecord? {
        val full = raw.trim()
        if (full.isEmpty()) return null
        val namespace = full.substringBefore(':', "").ifEmpty { "other" }
        val name = full.substringAfter(':', full).ifEmpty { return null }
        return TagRecord(
            namespace = namespace,
            name = name,
            full = full,
            translated = runCatching {
                com.hippo.ehviewer.client.EhTagDatabase.translate(name)
            }.getOrNull(),
            updatedAt = now,
        )
    }
}
