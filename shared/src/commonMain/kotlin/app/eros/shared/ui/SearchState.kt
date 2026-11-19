// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import com.hippo.ehviewer.util.AppLog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.GalleryFeed
import app.eros.shared.data.GalleryMetaEnricher.enrichRetryLoop
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.data.EhApi
import app.eros.shared.data.withEhErrors
import app.eros.shared.data.createEhApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 搜索状态：关键词 + 游标分页。
 * 站点搜索支持标签语法（如 `artist:sena$`、`female:bikini`）。
 */
class SearchState(
    private val api: EhApi = createEhApi(),
) {

    private val source = api
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var query by mutableStateOf("")
        private set

    var items by mutableStateOf<List<GalleryUi>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    var loadingMore by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var searched by mutableStateOf(false)
        private set

    var hiddenCount by mutableStateOf(0)
        private set

    /** 下拉刷新中（与首屏 [loading]、翻页 [loadingMore] 分开，避免刷新时整页变骨架）。 */
    var refreshing by mutableStateOf(false)
        private set

    /** 后端游标耗尽，列表尾部显示「没有更多了」。 */
    var endReached by mutableStateOf(false)
        private set

    private var nextHref: String? = null
    private var uploaderMode = false

    // 每次新搜索/清空都自增：用来丢弃「已经离开这次搜索」的旧请求，避免旧结果回填。
    private var generation = 0

    /** 关键词 → 已富化的结果，切回同一关键词时瞬间出卡片（对齐首页的分类内存缓存）。 */
    private val memoryCache = mutableMapOf<String, List<GalleryUi>>()

    /** 回到默认状态：下次打开搜索页不该还留着上次的关键词与结果。 */
    fun reset() {
        generation++
        query = ""
        uploaderMode = false
        items = emptyList()
        loading = false
        loadingMore = false
        error = null
        searched = false
        hiddenCount = 0
        refreshing = false
        endReached = false
        nextHref = null
    }

    /** 按上传者搜索（详情页点上传者）。 */
    fun searchUploader(name: String) {
        generation++
        hiddenCount = 0
        val q = name.trim()
        if (q.isBlank()) return
        uploaderMode = true
        query = "uploader:$q"
        searched = true
        scope.launch { GalleryCache.recordSearch("uploader:$q") }
        startFresh()
    }

    fun search(text: String) {
        generation++
        hiddenCount = 0
        uploaderMode = false
        val q = text.trim()
        if (q.isBlank()) return
        query = q
        searched = true
        scope.launch { GalleryCache.recordSearch(q) }
        startFresh()
    }

    /** 新关键词：先吃内存缓存（同一关键词二次搜索秒出），再拉网络页。 */
    private fun startFresh() {
        error = null
        endReached = false
        nextHref = null
        val mem = memoryCache[GalleryCache.searchKey(uploaderMode, query)]
        items = mem ?: emptyList()
        loading = mem == null
        scope.launch { run(append = false) }
    }

    fun loadMore() {
        val cursor = nextHref ?: return
        if (loading || loadingMore || refreshing) return
        scope.launch { run(append = true, cursor = cursor) }
    }

    /** 下拉刷新：保留当前结果重新拉第一页，不整页变骨架。 */
    fun refresh() {
        if (query.isBlank() || loading || refreshing) return
        refreshing = true
        scope.launch { run(append = false, forceRefresh = true) }
    }

    /** 统一错误态的重试入口：清空结果后按当前 query 重新搜索。 */
    fun retry() {
        if (query.isBlank()) return
        items = emptyList()
        nextHref = null
        error = null
        endReached = false
        scope.launch { run(append = false, forceRefresh = true) }
    }

    private suspend fun run(
        append: Boolean,
        cursor: String? = null,
        forceRefresh: Boolean = false,
    ) {
        val gen = generation
        val key = GalleryCache.searchKey(uploaderMode, query)
        if (!append && items.isEmpty() && !forceRefresh) {
            val cached = GalleryCache.page(key)
            if (cached != null) {
                val cachedFiltered = app.eros.shared.filter.EffectiveFilters.filter(cached.items)
                items = cachedFiltered
                nextHref = cached.nextHref
                endReached = cached.nextHref == null
                hiddenCount = cached.items.size - cachedFiltered.size
                loading = false
            }
        }
        if (append) loadingMore = true else if (items.isEmpty()) loading = true
        error = null
        try {
            val page = withEhErrors({ error = it }) {
                if (uploaderMode) {
                    source.searchByUploader(query.removePrefix("uploader:"), if (append) cursor else null)
                } else {
                    source.search(query, if (append) cursor else null)
                }
            }
            if (page != null && gen == generation) {
                // 与首页/收藏/排行一致：先把这一页 gdata 富化完（最多 PAGE_TIMEOUT_MS）再发布，
                // 卡片一出现标签与评分就是齐的，不会后补造成跳动。
                val feed = GalleryFeed.enrich(source, page.items)
                val enriched = feed.items
                if (!append) hiddenCount = feed.hidden else hiddenCount += feed.hidden
                items = if (append) {
                    val seen = HashSet<Long>(items.size * 2)
                    items.forEach { seen.add(it.gid) }
                    items + enriched.filter { seen.add(it.gid) }
                } else {
                    enriched
                }
                nextHref = page.nextHref
                endReached = page.nextHref == null
                memoryCache[key] = items
                GalleryCache.saveMerged(key, items, page.nextHref)
                scheduleEnrichRetry(enriched, feed.failed)
                AppLog.i("Search", "q=$query append=$append got=${page.items.size} total=${items.size} next=${page.nextHref != null} enrichFailed=${feed.failed.size}")
            } else {
                AppLog.w("Search", "FAILED q=$query: $error")
            }
        } finally {
            if (gen == generation) {
                loading = false
                loadingMore = false
                refreshing = false
            }
        }
    }

    /** 后台补课：没拿到的 gdata 继续重试，成功后原地替换并落盘。 */
    private fun scheduleEnrichRetry(published: List<GalleryUi>, failed: Set<Long>) {
        if (published.isEmpty() || failed.isEmpty()) return
        val q = query
        val uploader = uploaderMode
        scope.enrichRetryLoop(
            api = source,
            items = published,
            failedGids = failed,
            active = { query == q && uploaderMode == uploader },
        ) { updated ->
            items = GalleryFeed.applyRetry(items, updated)
            val key = GalleryCache.searchKey(uploader, q)
            memoryCache[key] = items
            GalleryCache.saveMerged(key, items, nextHref)
        }
    }
}

/** 常用标签快捷入口，点一下填进搜索框。 */
internal val SEARCH_HINTS = listOf(
    "female:bikini",
    "language:chinese$",
    "artist:sena",
    "male:solemale",
    "language:japanese$",
    "other:full color",
)
