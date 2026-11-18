// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import com.hippo.ehviewer.util.AppLog

import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.unit.dp
import app.eros.shared.data.GalleryFeed
import app.eros.shared.data.GalleryMetaEnricher
import app.eros.shared.data.GalleryMetaEnricher.enrichRetryLoop
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.data.EhApi
import app.eros.shared.data.toEhError
import app.eros.shared.data.withEhErrors
import app.eros.shared.data.userMessage
import app.eros.shared.data.createEhApi
import app.eros.shared.filter.GalleryFilter
import app.eros.shared.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import app.eros.shared.ui.chrome.LocalAppBottomBarInset

/**
 * 收藏夹 chips。抽成独立 composable 是为了让首屏骨架覆盖层也能画出同一行。
 */
@Composable
private fun FavouritesCatRow(state: FavouritesState, scrollState: ScrollState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val allSelected = state.currentCat == FavouritesState.ALL
        val allCount = state.catCounts.sum()
        Box(
            modifier = Modifier
                .squircleSurface(
                    color = if (allSelected) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.surfaceVariant,
                    cornerRadius = 12.dp,
                )
                .clickable { state.selectCat(FavouritesState.ALL) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                if (allCount > 0) "全部 ($allCount)" else "全部",
                style = MiuixTheme.textStyles.footnote2,
                color = if (allSelected) MiuixTheme.colorScheme.onPrimary
                else MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }
        (0..9).forEach { cat ->
            val name = state.catNames.getOrNull(cat)?.takeIf { it.isNotBlank() } ?: "收藏夹 $cat"
            val count = state.catCounts.getOrNull(cat) ?: 0
            val selected = state.currentCat == cat
            Box(
                modifier = Modifier
                    .squircleSurface(
                        color = if (selected) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.surfaceVariant,
                        cornerRadius = 12.dp,
                    )
                    .clickable { state.selectCat(cat) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    "$name ($count)",
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (selected) MiuixTheme.colorScheme.onPrimary
                    else MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * 收藏页状态：每个分类独立缓存 + 游标分页。
 */
class FavouritesState(
    private val api: EhApi = createEhApi(),
) {

    companion object {
        /** 伪分类：把 10 个收藏夹按发布时间倒序聚合成一条流。 */
        const val ALL = -1

        /** 「全部」一次并行抓几个文件夹，别把 10 个请求一口气打满。 */
        private const val ALL_FETCH_CONCURRENCY = 4
    }

    private val source = api
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 每个分类独立缓存：切回已加载过的分类直接命中，不重新请求。 */
    private val cache = mutableMapOf<Int, List<GalleryUi>>()
    private val cursors = mutableMapOf<Int, String?>()
    private val endByCat = mutableMapOf<Int, Boolean>()
    private val hiddenByCat = mutableMapOf<Int, Int>()

    /** 「全部」用：每个文件夹各自的下一页游标与「已到底」标记。 */
    private val allCursor = mutableMapOf<Int, String?>()
    private val allEndedFolder = mutableMapOf<Int, Boolean>()

    var items by mutableStateOf<List<GalleryUi>>(emptyList())
        private set

    var catNames by mutableStateOf<List<String>>(emptyList())
        private set

    var catCounts by mutableStateOf<List<Int>>(emptyList())
        private set

    /** 默认落在「全部」这一栏：一次把各收藏夹合并成按时间倒序的一条流。 */
    var currentCat by mutableStateOf(ALL)
        private set

    /**
     * 已经为哪个分类做过「滚到顶部」。
     *
     * 之前直接 `LaunchedEffect(currentCat) { gridState.scrollToItem(0) }`，
     * 从详情/阅读器返回时页面会重新进入组合、LaunchedEffect 重启，于是又滚回顶部；
     * 用它记住已处理过的分类，只有分类真的变了才归零。
     */
    internal var anchoredCat by mutableIntStateOf(Int.MIN_VALUE)

    var loadingCat by mutableStateOf<Int?>(null)
        private set

    var loadingMore by mutableStateOf(false)
        private set

    var endReached by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var signedIn by mutableStateOf(false)
        private set

    var hiddenCount by mutableStateOf(0)
        private set

    val loading: Boolean get() = loadingCat != null

    /**
     * 收藏夹 chips 的横向滚动状态。
     *
     * 骨架覆盖层会再画一行同样的 chips 用来对齐骨架，两处必须是同一个
     * [ScrollState]，否则「真实行已横向滑动、覆盖层那行还在最左」会叠出
     * 两层错位的按钮（用户看到的就是「滑动点击后按钮错位」）。
     */
    val catRowScroll = ScrollState(0)

    fun ensureLoaded() {
        signedIn = source.isSignedIn()
        if (!signedIn) return
        if (cache.containsKey(currentCat) || loadingCat != null) return
        load(currentCat)
    }

    fun selectCat(cat: Int) {
        if (cat == currentCat) return
        currentCat = cat
        error = null
        // 命中缓存：瞬间切换，不显示骨架
        cache[cat]?.let { cached ->
            items = cached
            endReached = endByCat[cat] ?: false
            hiddenCount = hiddenByCat[cat] ?: 0
            return
        }
        // 未命中：清空 + 骨架 + 加载
        items = emptyList()
        endReached = false
        hiddenCount = 0
        load(cat)
    }

    fun refresh() {
        cache.remove(currentCat)
        cursors.remove(currentCat)
        endByCat.remove(currentCat)
        if (currentCat == ALL) {
            allCursor.clear()
            allEndedFolder.clear()
        }
        load(currentCat)
    }

    /** 加载失败后重试当前分类。 */
    fun retry() {
        cache.remove(currentCat)
        cursors.remove(currentCat)
        endByCat.remove(currentCat)
        if (currentCat == ALL) {
            allCursor.clear()
            allEndedFolder.clear()
        }
        error = null
        load(currentCat)
    }

    /** 富化失败项的后台补课：一直重试到补齐或切走分类，合进列表并落盘。 */
    private fun scheduleEnrichRetry(cat: Int, published: List<GalleryUi>, failed: Set<Long>) {
        if (failed.isEmpty()) return
        scope.enrichRetryLoop(source, published, failed, active = { currentCat == cat }) { updated ->
            val before = items.size
            items = GalleryFeed.applyRetry(items, updated)
            if (items.size != before) hiddenCount += before - items.size
            cache[cat] = items
            // 补全后的列表落盘，下次冷启动缓存即“完整”，可直接秒开。
            GalleryCache.saveMerged(GalleryCache.favKey(cat), items, cursors[cat])
        }
    }

    fun loadMore() {
        if (loadingMore || loadingCat != null) return
        if (currentCat == ALL) {
            loadAll(reset = false)
            return
        }
        val cursor = cursors[currentCat] ?: return
        val cat = currentCat
        scope.launch {
            loadingMore = true
            val page = withEhErrors({ if (currentCat == cat) error = it }) { source.favourites(cat, cursor) }
            if (page != null) {
                val existing = cache[cat].orEmpty()
                val feed = GalleryFeed.enrich(source, page.items)
                val enriched = feed.items
                val hidden = (hiddenByCat[cat] ?: 0) + feed.hidden
                hiddenByCat[cat] = hidden
                val seen = HashSet<Long>(existing.size * 2)
                existing.forEach { seen.add(it.gid) }
                val merged = existing + enriched.filter { seen.add(it.gid) }
                cache[cat] = merged
                cursors[cat] = page.nextHref
                endByCat[cat] = page.nextHref == null
                GalleryCache.saveMerged(GalleryCache.favKey(cat), merged, page.nextHref)
                if (currentCat == cat) {
                    items = merged
                    endReached = page.nextHref == null
                    hiddenCount = hidden
                    scheduleEnrichRetry(cat, enriched, feed.failed)
                }
            }
            loadingMore = false
        }
    }

    /** 本次「全部」要扫的文件夹；还不知道计数时先按 10 个全扫。 */
    private fun foldersToScan(): List<Int> {
        val counts = catCounts
        if (counts.isEmpty()) return (0..9).toList()
        val nonEmpty = counts.indices.filter { counts[it] > 0 }
        return if (nonEmpty.isEmpty()) (0..9).toList() else nonEmpty
    }

    /**
     * 「全部收藏」：并行取每个文件夹的下一页，按发布时间倒序合并。
     *
     * 站点没有跨文件夹的接口，只能一个一个夹取；用 4 路并发控制在弱网下也不至于打满。
     * `reset = true` 时从每个夹的第一页重新开始。
     */
    private fun loadAll(reset: Boolean) {
        if (loadingMore || loadingCat != null) return
        if (reset) {
            cache.remove(ALL)
            endByCat.remove(ALL)
            allCursor.clear()
            allEndedFolder.clear()
            items = emptyList()
            endReached = false
            hiddenCount = 0
            loadingCat = ALL
        } else {
            loadingMore = true
        }
        error = null
        scope.launch {
            val folders = foldersToScan()
            val merged = LinkedHashMap<Long, GalleryUi>()
            cache[ALL].orEmpty().forEach { merged[it.gid] = it }
            var failure: String? = null
            coroutineScope {
                val gate = Semaphore(ALL_FETCH_CONCURRENCY)
                val jobs = folders.map { folder ->
                    async {
                        gate.withPermit { runCatching { source.favourites(folder, allCursor[folder]) } }
                    }
                }
                jobs.forEachIndexed { index, deferred ->
                    val folder = folders[index]
                    deferred.await()
                        .onSuccess { page ->
                            allCursor[folder] = page.nextHref
                            allEndedFolder[folder] = page.nextHref == null
                            if (page.catNames.isNotEmpty()) catNames = page.catNames
                            if (page.catCounts.isNotEmpty()) catCounts = page.catCounts
                            page.items.forEach { merged[it.gid] = it }
                        }
                        .onFailure { e ->
                            if (e is CancellationException) throw e
                            if (failure == null) failure = e.toEhError().userMessage()
                        }
                }
            }
            val filtered = app.eros.shared.filter.EffectiveFilters
                .filter(merged.values.toList())
                .sortedByDescending { it.posted ?: "" }
            val ended = folders.all { allEndedFolder[it] == true }
            cache[ALL] = filtered
            endByCat[ALL] = ended
            hiddenByCat[ALL] = merged.size - filtered.size
            if (currentCat == ALL) {
                items = filtered
                endReached = ended
                hiddenCount = merged.size - filtered.size
                if (failure != null) error = failure
                // 标签要 gdata 才有：先出内容，再在后台把这一批补齐。
                val enriched = GalleryFeed.enrich(source, filtered)
                if (currentCat == ALL && enriched.items.isNotEmpty()) {
                    items = enriched.items
                    cache[ALL] = enriched.items
                    hiddenCount = merged.size - filtered.size + enriched.hidden
                    scheduleEnrichRetry(ALL, enriched.items, enriched.failed)
                }
            }
            AppLog.i("Fav", "all folders=${folders.size} loaded=${filtered.size} ended=$ended")
            if (loadingCat == ALL) loadingCat = null
            loadingMore = false
        }
    }

    private fun load(cat: Int) {
        cache[cat]?.let {
            items = it
            endReached = endByCat[cat] ?: false
            return
        }
        if (cat == ALL) {
            loadAll(reset = true)
            return
        }
        // P0：先读本地缓存立即出内容（缺的字段由卡片骨架顶着），再走网络刷新。
        val key = GalleryCache.favKey(cat)
        scope.launch {
            if (items.isEmpty()) {
                val cached = GalleryCache.page(key)
                if (cached != null) {
                    val cachedFiltered = app.eros.shared.filter.EffectiveFilters.filter(cached.items)
                    cache[cat] = cachedFiltered
                    cursors[cat] = cached.nextHref
                    endByCat[cat] = cached.nextHref == null
                    if (currentCat == cat) {
                        items = cachedFiltered
                        endReached = cached.nextHref == null
                        hiddenCount = cached.items.size - cachedFiltered.size
                    }
                }
            }
            if (items.isEmpty()) loadingCat = cat
            error = null
            val page = withEhErrors({ if (currentCat == cat) error = it }) { source.favourites(cat, null) }
            if (page != null) {
                val feed = GalleryFeed.enrich(source, page.items)
                val enriched = feed.items
                hiddenByCat[cat] = feed.hidden
                cache[cat] = enriched
                cursors[cat] = page.nextHref
                endByCat[cat] = page.nextHref == null
                if (page.catNames.isNotEmpty()) catNames = page.catNames
                if (page.catCounts.isNotEmpty()) catCounts = page.catCounts
                GalleryCache.saveMerged(key, enriched, page.nextHref)
                if (currentCat == cat) {
                    items = enriched
                    endReached = page.nextHref == null
                    hiddenCount = feed.hidden
                    // 这批没补齐的项交给后台补课，不再在列表顶部挂一条可点提示。
                    scheduleEnrichRetry(cat, enriched, feed.failed)
                }
                signedIn = true
                AppLog.i("Fav", "cat=$cat loaded=${enriched.size} next=${page.nextHref != null}")
            } else {
                AppLog.w("Fav", "FAILED cat=$cat")
            }
            if (loadingCat == cat) loadingCat = null
        }
    }
}

/** 收藏页：自带顶栏（页面级 Scaffold），切 tab 时整页滑动。 */
@Composable
fun FavouritesScreen(
    state: FavouritesState,
    gridState: LazyGridState,
    onGalleryClick: (GalleryUi) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val barInset = LocalAppBottomBarInset.current
    LaunchedEffect(state) { state.ensureLoaded() }
    // 站点 Profile 切换后缓存已被清空，这里把屏上已渲染的列表也换掉。
    val profileRevision = AppSettings.profileRevision
    var seenProfileRevision by remember { mutableIntStateOf(profileRevision) }
    LaunchedEffect(profileRevision) {
        if (profileRevision != seenProfileRevision) {
            seenProfileRevision = profileRevision
            state.refresh()
        }
    }
    CoverPrefetchEffect(gridState, state.items)
                DetailPrefetchEffect(gridState, state.items)

    if (!state.signedIn) {
        ErosPageScaffold(
            title = "收藏",
            navigationIcon = { AccountEntry(onOpenAccount) },
            actions = { HistoryAndSearchEntries(onOpenHistory, onOpenSearch) },
        ) { _, _, _ ->
            ErosEmptyState(
                title = "收藏需要登录",
                description = "在设置 → 账号页完成登录后，这里会显示你的收藏夹",
            )
        }
        return
    }

    val nearBottom by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            state.items.size > 0 && info.visibleItemsInfo.any { it.index == state.items.size - 1 } && !state.loadingMore
        }
    }

    LaunchedEffect(nearBottom, state.items.size, state.currentCat) {
        if (nearBottom) state.loadMore()
    }

    // 只有分类真的切换时才回到顶部；从详情/阅读器返回时保持原位置。
    LaunchedEffect(state.currentCat) {
        if (state.anchoredCat != state.currentCat) {
            state.anchoredCat = state.currentCat
            gridState.scrollToItem(0)
        }
    }

    ErosPageScaffold(
        title = "收藏",
        navigationIcon = { AccountEntry(onOpenAccount) },
        actions = { HistoryAndSearchEntries(onOpenHistory, onOpenSearch) },
    ) { innerPadding, pageScrollBehavior, pageBackdrop ->
        Box(
            Modifier
                .fillMaxSize()
        ) {
            PullToRefresh(
                isRefreshing = state.loading && state.items.isNotEmpty(),
                onRefresh = { state.refresh() },
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = barInset + 12.dp,
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
                        bottom = barInset + 12.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "catRow") {
                        FavouritesCatRow(state, state.catRowScroll)
                    }

                    if (state.loading && state.items.isEmpty()) {
                        // 骨架由外层 [SkeletonGridOverlay] 负责淡出，这里不再画骨架项。
                    } else if (state.error != null && state.items.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ErosErrorState(
                                message = state.error,
                                onRetry = { state.retry() },
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                            )
                        }
                    } else if (state.items.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ErosEmptyState(
                                title = "这个收藏夹是空的",
                                description = "在详情页点收藏，选择一个收藏夹",
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                            )
                        }
                    } else {
                        itemsIndexed(state.items, key = { _, it -> it.gid }) { index, g ->
                            GalleryCard(
                                gallery = g,
                                index = index,
                                scrolling = gridState.isScrollInProgress,
                                onClick = { onGalleryClick(g) },
                            )
                        }
                        if (state.loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator() }
                            }
                        }

                        if (state.endReached) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
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

                // 首屏骨架整层覆盖，内容到达时淡出；收藏夹 chips 始终是真的。
                SkeletonGridOverlay(
                    visible = state.loading && state.items.isEmpty(),
                    columns = columns,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        bottom = barInset + 12.dp,
                    ),
                    header = { FavouritesCatRow(state, state.catRowScroll) },
                )
                }
            }
        }
    }
}
