// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eros.shared.data.GalleryFeed
import app.eros.shared.data.GalleryMetaEnricher
import app.eros.shared.data.GalleryMetaEnricher.enrichRetryLoop
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.data.RankPeriod
import app.eros.shared.data.EhApi
import app.eros.shared.data.toEhError
import app.eros.shared.data.withEhErrors
import app.eros.shared.ui.design.AppErrorState
import app.eros.shared.ui.design.AppScaffold
import app.eros.shared.data.userMessage
import app.eros.shared.data.createEhApi
import app.eros.shared.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 时段 chips（今日/本月/今年/全部）。抽成独立 composable 是为了让首屏骨架覆盖层
 * 也能画出同一行 —— 骨架期间上半部分就是真的。
 */
@Composable
private fun TopListPeriodRow(state: TopListState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RankPeriod.entries.forEachIndexed { index, (label, _) ->
            val selected = index == state.periodIndex
            Box(
                modifier = Modifier
                    .squircleSurface(
                        color = if (selected) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.surfaceVariant,
                        cornerRadius = 12.dp,
                    )
                    .clickable { state.selectPeriod(index) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (selected) MiuixTheme.colorScheme.onPrimary
                    else MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * 排行榜状态。
 *
 * 数据源：`toplist.php?tl=<tl>&p=<page>`（带 tl/p 时站点返回**紧凑画廊列表**并分页）。
 * tl：今日 15 / 本月 13 / 今年 12 / 全部 11。
 */
class TopListState(
    private val api: EhApi = createEhApi(),
) {

    private val source = api
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var periodIndex by mutableStateOf(0)
        private set

    /**
     * 已经为哪个周期做过「滚到顶部」。
     *
     * 之前直接 `LaunchedEffect(periodIndex) { gridState.scrollToItem(0) }`，
     * 从详情/阅读器返回时页面重新进入组合，效果重启导致又滚回顶部；
     * 记住已处理过的周期后，只有周期真的切换才归零。
     */
    internal var anchoredPeriod by mutableIntStateOf(Int.MIN_VALUE)

    var items by mutableStateOf<List<GalleryUi>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    var refreshing by mutableStateOf(false)
        private set

    var loadingMore by mutableStateOf(false)
        private set

    var endReached by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var hiddenCount by mutableStateOf(0)
        private set

    private var nextPage: Int? = 0
    private var loadedOnce = false

    /**
     * 按周期的内存缓存：来回点「今日/本月/今年/全部」时直接换列表，不再清空重载。
     * 与首页 `HomeState.cache[cat]` 同构，区别只是排行榜还要记分页游标和是否到底。
     */
    private val cache = mutableMapOf<Int, List<GalleryUi>>()
    private val cursorByTl = mutableMapOf<Int, Int?>()
    private val endByTl = mutableMapOf<Int, Boolean>()
    private val hiddenByTl = mutableMapOf<Int, Int>()

    private val tl: Int get() = RankPeriod.entries.getOrNull(periodIndex)?.second ?: RankPeriod.DAY

    fun ensureLoaded() {
        if (loadedOnce) return
        loadedOnce = true
        loadFirstPage(pullRefresh = false)
    }

    fun selectPeriod(index: Int) {
        if (index == periodIndex) return
        periodIndex = index
        // 缓存按 tl 存（15/13/12/11），不能按 chip 下标取。
        val targetTl = RankPeriod.entries.getOrNull(index)?.second ?: RankPeriod.DAY
        val cached = cache[targetTl]
        if (cached != null) {
            // 命中内存缓存：瞬切，不骨架、不网络、不富化。
            items = cached
            nextPage = cursorByTl[targetTl] ?: 0
            endReached = endByTl[targetTl] ?: false
            hiddenCount = hiddenByTl[targetTl] ?: 0
            error = null
            loading = false
            println("[TopList] tl=$targetTl switch from memory items=${cached.size}")
            return
        }
        items = emptyList()
        hiddenCount = 0
        error = null
        endReached = false
        nextPage = 0
        loading = true
        loadFirstPage(pullRefresh = false)
    }

    fun refresh() = loadFirstPage(pullRefresh = true)

    fun retry() = loadFirstPage(pullRefresh = false, force = true)

    private fun loadFirstPage(pullRefresh: Boolean, force: Boolean = pullRefresh) {
        val startTl = tl
        if (pullRefresh) refreshing = true else if (items.isEmpty()) loading = true
        error = null
        scope.launch {
            val key = GalleryCache.rankKey(startTl)
            if (!force && items.isEmpty()) {
                val cached = GalleryCache.page(key)
                if (cached != null) {
                    val cachedFiltered = app.eros.shared.filter.EffectiveFilters.filter(cached.items)
                    // 先读缓存立即出内容：卡片高度固定、标签区留白，缺元信息由骨架顶着，
                    // 不会因为“标签后到”产生二次布局，所以不再等“完整”缓存。
                    hiddenCount = cached.items.size - cachedFiltered.size
                    nextPage = cached.nextHref?.toIntOrNull()
                    endReached = nextPage == null
                    items = cachedFiltered
                    loading = false
                    rememberInMemory(startTl, cachedFiltered)
                }
            }
            // 排行榜紧凑版式自带标题/分类/页数/评分/时间，标签只有 gdata 有：
            // 这里不再同步等 gdata（那是 2~5s 的阻塞），标签交给下面的富化 + 后台补课。
            val page = withEhErrors({ error = it }) {
                    source.rankList(startTl, 0, forceRefresh = force, enrichTags = false)
                }
            if (startTl != tl) {
                if (pullRefresh) refreshing = false
                return@launch
            }
            if (page == null) {
                if (pullRefresh) refreshing = false else loading = false
                println("[TopList] FAILED: $error")
                return@launch
            }
            val filtered = mergeKnownMeta(
                app.eros.shared.filter.EffectiveFilters.filter(page.items),
                items,
            )
            // 先发布、再富化：HTML 一到就换列表，标签/评分缺的由骨架顶着，随后原地补齐。
            hiddenCount = page.items.size - filtered.size
            items = filtered
            nextPage = page.nextPage
            endReached = page.nextPage == null
            rememberInMemory(startTl, filtered)
            if (pullRefresh) refreshing = false else loading = false
            println(
                "[TopList] tl=$startTl page=0 items=${filtered.size} next=${page.nextPage} total=${page.totalPages}",
            )
            val res = GalleryMetaEnricher.enrichPage(source, filtered)
            if (startTl != tl) return@launch
            if (res.updated.isNotEmpty()) {
                val before = items.size
                // 标签屏蔽依赖 gdata 标签：富化之后再过滤一遍才会真的生效。
                items = GalleryFeed.applyRetry(items, res.updated)
                hiddenCount += before - items.size
                rememberInMemory(startTl, items)
                println("[TopList] tl=$startTl enriched +${res.updated.size} hidden=${before - items.size}")
            }
            scheduleEnrichRetry(startTl, items, res.failedGids)
            GalleryCache.saveMerged(key, items, page.nextPage?.toString())
        }
    }

    /** 把当前列表写进内存缓存，切周期时直接复用。 */
    private fun rememberInMemory(tlValue: Int, list: List<GalleryUi>) {
        cache[tlValue] = list
        cursorByTl[tlValue] = nextPage
        endByTl[tlValue] = endReached
        hiddenByTl[tlValue] = hiddenCount
    }

    /**
     * 网络页回来时保留已知的标签/评分/页数/时间。
     *
     * 排行榜的紧凑页只有标题、分类、页数、评分、时间，**没有标签**；如果直接用它覆盖
     * 当前列表，缓存里已经补好的标签会先消失、等富化回来再出现，看起来像闪一下。
     * 这里按 gid 把旧值补回去，新页真给了值就以新页为准。
     */
    private fun mergeKnownMeta(fresh: List<GalleryUi>, previous: List<GalleryUi>): List<GalleryUi> {
        if (previous.isEmpty()) return fresh
        val old = previous.associateBy { it.gid }
        return fresh.map { item ->
            val known = old[item.gid] ?: return@map item
            item.copy(
                tags = if (item.tags.isEmpty()) known.tags else item.tags,
                pages = if (item.pages <= 0) known.pages else item.pages,
                rating = if (item.rating < 0f) known.rating else item.rating,
                posted = item.posted ?: known.posted,
            )
        }
    }

    /** 富化失败项的后台补课：一直重试到补齐或切走周期，合进列表并落盘。 */
    private fun scheduleEnrichRetry(tlValue: Int, published: List<GalleryUi>, failed: Set<Long>) {
        if (failed.isEmpty()) return
        scope.enrichRetryLoop(source, published, failed, active = { tl == tlValue }) { updated ->
            val before = items.size
            items = GalleryFeed.applyRetry(items, updated)
            hiddenCount += before - items.size
            rememberInMemory(tlValue, items)
            GalleryCache.saveMerged(
                GalleryCache.rankKey(tlValue),
                items,
                nextPage?.toString(),
            )
        }
    }

    fun loadMore() {
        if (loadingMore || loading || refreshing || endReached) return
        val page = nextPage ?: return
        val currentTl = tl
        scope.launch {
            loadingMore = true
            try {
                val result = source.rankList(
                        currentTl,
                        page,
                        enrichTags = false,
                    )
                if (currentTl != tl) return@launch
                val filtered = app.eros.shared.filter.EffectiveFilters.filter(result.items)
                hiddenCount += result.items.size - filtered.size
                // 与首屏一致：先拼进列表（标签位由骨架顶着），再原地富化。
                val seen = HashSet<Long>(items.size * 2)
                items.forEach { seen.add(it.gid) }
                items = items + filtered.filter { seen.add(it.gid) }
                nextPage = result.nextPage
                endReached = result.nextPage == null
                rememberInMemory(currentTl, items)
                println("[TopList] tl=$currentTl page=$page +${filtered.size} total=${items.size} end=$endReached")
                val res = GalleryMetaEnricher.enrichPage(source, filtered)
                if (currentTl != tl) return@launch
                if (res.updated.isNotEmpty()) {
                    val before = items.size
                    items = GalleryFeed.applyRetry(items, res.updated)
                    hiddenCount += before - items.size
                    rememberInMemory(currentTl, items)
                }
                scheduleEnrichRetry(currentTl, items, res.failedGids)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                println("[TopList] loadMore FAILED: ${t.message}")
            } finally {
                GalleryCache.saveMerged(
                    GalleryCache.rankKey(currentTl),
                    items,
                    nextPage?.toString(),
                )
                loadingMore = false
            }
        }
    }
}

/**
 * 排行榜页：与首页/收藏页完全同构——
 * `Scaffold + ErosTopBar` → `Box(layerBackdrop)` → `PullToRefresh` → `LazyVerticalGrid`，
 * 网格自带顶部内边距把内容顶到栏下，这样顶栏的渐进模糊才能取到内容。
 */
@Composable
fun TopListScreen(
    state: TopListState,
    gridState: LazyGridState,
    onGalleryClick: (GalleryUi) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAccount: () -> Unit,
    bottomInset: Dp = 0.dp,
) {
    LaunchedEffect(Unit) { state.ensureLoaded() }
    // 站点 Profile 切换后缓存已被清空，这里把屏上已渲染的列表也换掉。
    val profileRevision = AppSettings.profileRevision
    var seenProfileRevision by remember { mutableIntStateOf(profileRevision) }
    LaunchedEffect(profileRevision) {
        if (profileRevision != seenProfileRevision) {
            seenProfileRevision = profileRevision
            state.refresh()
        }
    }
    // 只有周期真的切换时才回到顶部；从详情/阅读器返回时保持原位置。
    LaunchedEffect(state.periodIndex) {
        if (state.anchoredPeriod != state.periodIndex) {
            state.anchoredPeriod = state.periodIndex
            gridState.scrollToItem(0)
        }
    }

    CoverPrefetchEffect(gridState, state.items)
                DetailPrefetchEffect(gridState, state.items)

    val nearBottom by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val triggerAt = (state.items.size * 0.7f).toInt().coerceAtLeast(1)
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= triggerAt && !state.loadingMore
        }
    }
    LaunchedEffect(nearBottom, state.items.size, state.endReached) {
        if (nearBottom) state.loadMore()
    }

    AppScaffold(
        title = "排行榜",
        navigationIcon = { AccountEntry(onOpenAccount) },
        actions = { HistoryAndSearchEntries(onOpenHistory, onOpenSearch) },
    ) { innerPadding, pageScrollBehavior, pageBackdrop ->
        Box(
            Modifier
                .fillMaxSize()
                .then(if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier),
        ) {
            PullToRefresh(
                isRefreshing = state.refreshing,
                onRefresh = { state.refresh() },
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = bottomInset + 12.dp,
                ),
                topAppBarScrollBehavior = pageScrollBehavior,
            ) {
                // 列数按容器实测宽度算：rail 展开时窗口宽 ≠ 内容宽，按窗口算会多一列。
                BoxWithConstraints(Modifier.fillMaxSize()) {
                val columns = if (AppSettings.cardStyle == "download") {
                    1
                } else {
                    rememberGalleryColumns(maxWidth - GalleryGridSidePadding * 2)
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pageScrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        bottom = bottomInset + 12.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 时段 chips：与首页/收藏页的 chips 一样作为网格首项
                    item(span = { GridItemSpan(maxLineSpan) }, key = "periodRow") {
                        TopListPeriodRow(state)
                    }

                    when {
                        // 骨架由外层 [SkeletonGridOverlay] 负责淡出，这里不再画骨架项。
                        state.loading && state.items.isEmpty() -> Unit

                        state.error != null && state.items.isEmpty() -> item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "error",
                        ) {
                            AppErrorState(onRetry = { state.retry() })
                        }

                        else -> {
                            itemsIndexed(state.items, key = { _, it -> it.gid }) { index, item ->
                                GalleryCard(
                                    gallery = item,
                                    index = index,
                                    scrolling = gridState.isScrollInProgress,
                                    onClick = { onGalleryClick(item) },
                                )
                            }

                            if (state.loadingMore) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "loadingMore") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { CircularProgressIndicator() }
                                }
                            }

                            if (state.endReached && state.items.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "end") {
                                    Box(
                                        Modifier.fillMaxWidth().height(48.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
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

                // 首屏骨架整层覆盖，内容到达时淡出；上半部分的时段 chips 始终是真的。
                SkeletonGridOverlay(
                    visible = state.loading && state.items.isEmpty(),
                    columns = columns,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        bottom = bottomInset + 12.dp,
                    ),
                    header = { TopListPeriodRow(state) },
                )
                }
            }
        }
    }
}
