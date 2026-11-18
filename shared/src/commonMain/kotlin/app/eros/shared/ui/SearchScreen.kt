// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import com.hippo.ehviewer.util.AppLog

import top.yukonga.miuix.kmp.basic.InputField

import top.yukonga.miuix.kmp.basic.SearchBar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.eros.shared.data.GalleryRef
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.GalleryLink
import app.eros.shared.data.GalleryFeed
import app.eros.shared.data.GalleryMetaEnricher
import app.eros.shared.data.GalleryMetaEnricher.enrichRetryLoop
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.data.cache.SearchHistoryEntry
import app.eros.shared.data.cache.TagRecord
import app.eros.shared.settings.AppSettings
import app.eros.shared.data.EhApi
import app.eros.shared.data.toEhError
import app.eros.shared.data.withEhErrors
import app.eros.shared.data.userMessage
import app.eros.shared.data.createEhApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

    /** 关键词 → 已富化的结果，切回同一关键词时瞬间出卡片（对齐首页的分类内存缓存）。 */
    private val memoryCache = mutableMapOf<String, List<GalleryUi>>()

    /** 按上传者搜索（详情页点上传者）。 */
    fun searchUploader(name: String) {
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
            if (page != null) {
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
            loading = false
            loadingMore = false
            refreshing = false
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
private val SEARCH_HINTS = listOf(
    "female:bikini",
    "language:chinese$",
    "artist:sena",
    "male:solemale",
    "language:japanese$",
    "other:full color",
)

@Composable
fun SearchScreen(
    state: SearchState,
    onBack: () -> Unit,
    onGalleryClick: (GalleryUi) -> Unit,
    onOpenGallery: ((GalleryRef) -> Unit)? = null,
) {
    val gridState = rememberLazyGridState()
    CoverPrefetchEffect(gridState, state.items)
                DetailPrefetchEffect(gridState, state.items)
    var input by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<TagRecord>>(emptyList()) }
    var recent by remember { mutableStateOf<List<SearchHistoryEntry>>(emptyList()) }
    val scope = rememberCoroutineScope()
    // 搜索是独立路由，没有 App 底栏，只需要让内容避开系统导航栏；
    // 页面背景（含底部）由 ErosPageScaffold 的 backdrop 铺满，保证沉浸。
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(state.searched) {
        if (!state.searched) {
            recent = runCatching { GalleryCache.searchHistory(8) }.getOrDefault(emptyList())
        }
    }
    LaunchedEffect(expanded, input) {
        val q = input.trim()
        if (!expanded || q.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(180)
        suggestions = runCatching { GalleryCache.searchTags(q, 10) }.getOrDefault(emptyList())
    }

    // 外部触发搜索（点标签/上传者）时把关键词回填到输入框
    // 站点 Profile 切换后缓存已被清空，这里把屏上已渲染的结果也换掉。
    val profileRevision = AppSettings.profileRevision
    var seenProfileRevision by remember { mutableIntStateOf(profileRevision) }
    LaunchedEffect(profileRevision) {
        if (profileRevision != seenProfileRevision) {
            seenProfileRevision = profileRevision
            state.refresh()
        }
    }

    LaunchedEffect(state.query) {
        if (state.query.isNotBlank() && state.query != input) input = state.query
    }

    val nearBottom by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            // 网格第 0 项是搜索框（未搜索时第 1 项是提示列表），卡片从 headerCount 开始，
            // 否则「最后一项可见」会永远命中搜索框那一项。
            val headerCount = if (state.searched) 1 else 2
            val lastIndex = headerCount + state.items.size - 1
            state.items.isNotEmpty() && !state.loadingMore && info.visibleItemsInfo.any { it.index >= lastIndex }
        }
    }

    LaunchedEffect(nearBottom, state.items.size) {
        if (nearBottom) state.loadMore()
    }

    ErosPageScaffold(title = "搜索", onBack = onBack) { innerPadding, pageScrollBehavior, pageBackdrop ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
        ) {
            // 与首页同构：网格从 y=0 铺满，顶栏与导航栏的高度只体现在 contentPadding 里，
            // 这样滚动时卡片会从模糊顶栏后面滑过，栏模糊才有内容可采样。
            val gridPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = navBottom + 12.dp,
            )
            // 列数按容器实测宽度算：rail 展开时窗口宽 ≠ 内容宽，按窗口算会多一列。
            val columns = if (AppSettings.cardStyle == "download") {
                1
            } else {
                rememberGalleryColumns(maxWidth - GalleryGridSidePadding * 2)
            }
            // 搜索框在网格里的实际高度（含间距），首屏骨架要用它让位，
            // 否则骨架卡片会盖在搜索框上。
            var searchBarHeight by remember { mutableStateOf(0) }
            val density = LocalDensity.current
            PullToRefresh(
                isRefreshing = state.refreshing,
                onRefresh = { if (state.searched) state.refresh() },
                modifier = Modifier.fillMaxSize(),
                contentPadding = gridPadding,
                topAppBarScrollBehavior = pageScrollBehavior,
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pageScrollBehavior.nestedScrollConnection),
                    contentPadding = gridPadding,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 搜索框作为网格头部（等价于首页的 HomeCatRow）：随列表一起滚动，
                    // 顶栏后面始终有内容经过，模糊才生效。
                    item(span = { GridItemSpan(maxLineSpan) }, key = "searchBar") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { searchBarHeight = it.size.height },
                        ) {
                            SearchBar(
                                modifier = Modifier.fillMaxWidth(),
                                inputField = {
                                    InputField(
                                        query = input,
                                        onQueryChange = { input = it },
                                        onSearch = {
                                            // 粘贴画廊链接时直接进详情，不再当关键词搜。
                                            val ref = GalleryLink.first(input)
                                            when {
                                                ref != null && onOpenGallery != null -> {
                                                    onOpenGallery.invoke(ref)
                                                    input = ""
                                                }
                                                input.isNotBlank() -> state.search(input)
                                            }
                                            expanded = false
                                        },
                                        expanded = expanded,
                                        onExpandedChange = { expanded = it },
                                        label = "搜索标签或关键词",
                                    )
                                },
                                outsideEndAction = {
                                    Text(
                                        modifier = Modifier
                                            .padding(end = 16.dp)
                                            .clickable(
                                                interactionSource = null,
                                                indication = null,
                                                onClick = { expanded = false },
                                            ),
                                        text = "取消",
                                        style = MiuixTheme.textStyles.button,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                },
                                expanded = expanded,
                                onExpandedChange = { expanded = it },
                            ) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    if (input.isNotBlank()) {
                                        Text(
                                            "搜索「${input.trim()}」",
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    state.search(input)
                                                    expanded = false
                                                }
                                                .padding(vertical = 10.dp),
                                        )
                                    }
                                    suggestions.forEach { tag ->
                                        val label = tag.translated ?: tag.name
                                        Text(
                                            label + "  ·  " + tag.full + "  ·  " + tag.count,
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val term = if (tag.name.contains(' ')) {
                                                        tag.namespace + ":\"" + tag.name + "\""
                                                    } else {
                                                        tag.full
                                                    }
                                                    input = term
                                                    state.search(term)
                                                    expanded = false
                                                }
                                                .padding(vertical = 9.dp),
                                        )
                                    }
                                    SEARCH_HINTS.forEach { hint ->
                                        Text(
                                            hint,
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    input = hint
                                                    state.search(hint)
                                                    expanded = false
                                                }
                                                .padding(vertical = 10.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!state.searched) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "hints") {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                if (recent.isNotEmpty()) {
                                    Text("最近搜索", style = MiuixTheme.textStyles.subtitle)
                                    Spacer(Modifier.height(6.dp))
                                    recent.forEach { entry ->
                                        Text(
                                            entry.query,
                                            style = MiuixTheme.textStyles.body2,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    input = entry.query
                                                    state.search(entry.query)
                                                }
                                                .padding(vertical = 9.dp),
                                        )
                                    }
                                    Text(
                                        "清空最近搜索",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable {
                                                scope.launch {
                                                    GalleryCache.clearSearchHistory()
                                                    recent = emptyList()
                                                }
                                            }
                                            .padding(vertical = 8.dp),
                                    )
                                    Spacer(Modifier.height(18.dp))
                                }
                                Text("语法示例", style = MiuixTheme.textStyles.subtitle)
                                Spacer(Modifier.height(4.dp))
                                SEARCH_HINTS.forEach { hint ->
                                    Text(
                                        hint,
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                input = hint
                                                state.search(hint)
                                            }
                                            .padding(vertical = 8.dp),
                                    )
                                }
                                Text(
                                    "支持 namespace:tag、多个词空格分隔、-tag 排除；含空格的标签用引号",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }

                    // 骨架由下面那层 [SkeletonGridOverlay] 负责淡出，这里不再画骨架项。
                    if (state.searched) {
                        itemsIndexed(state.items, key = { _, it -> it.gid }) { index, g ->
                            GalleryCard(
                                gallery = g,
                                index = index,
                                scrolling = gridState.isScrollInProgress,
                                onClick = { onGalleryClick(g) },
                            )
                        }
                        if (state.loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "loadingMore") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        val footerError = state.error
                        if (footerError != null && state.items.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "footerError") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        "加载失败，点按重试",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { state.retry() },
                                    )
                                }
                            }
                        }
                        if (state.endReached) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "endReached") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        "没有更多了",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 错误 / 空结果：放在懒加载项里会拿到无限高度约束（ErosErrorState 内部是
            // fillMaxSize），所以作为覆盖层居中显示；此时网格里本来也没有卡片。
            if (state.searched && state.items.isEmpty() && !state.loading) {
                Box(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
                    val blockingError = state.error
                    if (blockingError != null) {
                        ErosErrorState(message = blockingError, onRetry = { state.retry() })
                    } else {
                        ErosEmptyState(
                            title = "没有找到结果",
                            description = "换一组关键词，或检查搜索语法（支持 namespace:tag、-tag 排除）",
                        )
                    }
                }
            }

            // 搜索首屏骨架整层覆盖，结果到达时淡出；头部留出搜索框的高度。
            SkeletonGridOverlay(
                visible = state.loading && state.items.isEmpty(),
                columns = columns,
                contentPadding = gridPadding,
                header = {
                    if (searchBarHeight > 0) {
                        Spacer(Modifier.height(with(density) { searchBarHeight.toDp() }))
                    }
                },
            )
        }
    }
}
