// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import top.yukonga.miuix.kmp.squircle.squircleSurface
import com.hippo.ehviewer.util.AppLog

import androidx.compose.foundation.ScrollState

import androidx.compose.foundation.horizontalScroll


import top.yukonga.miuix.kmp.icon.extended.Search



import top.yukonga.miuix.kmp.basic.Icon




import top.yukonga.miuix.kmp.basic.ScrollBehavior


import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.eros.shared.data.GalleryFeed
import app.eros.shared.data.GalleryMetaEnricher
import app.eros.shared.data.GalleryMetaEnricher.enrichRetryLoop
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.settings.AppSettings
import app.eros.shared.settings.HomeCats
import app.eros.shared.settings.HomeEntry
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import app.eros.shared.ui.chrome.LocalAppBottomBarInset

/**
 * 首页分页状态。
 *
 * 注意：这个对象必须**由 App 层持有**，不能建在 HomeScreen 里——Tab 切换会让 HomeScreen
 * 离开组合，`remember` 的状态会被销毁，导致切回来重新请求、滚动位置丢失。
 *
 * e-hentai 首页是游标分页（`?next=<gid>`），下一页地址来自上一页的 HTML；
 * `nextHref == null` 即到底。
 */
/** 分类位掩码见 [app.eros.shared.settings.HomeCats]；这里只保留分类行自己的键。 */
private const val POPULAR_KEY = "popular"

/**
 * 首页顶部的分类行。
 *
 * 「热门」写死在最前且不可编辑；中间是 [entries]（内置分类与自定义分组混排，顺序就是
 * 设置里的顺序）；末尾固定一个「＋」用于新建分组。长按条目进入编辑，由 [onEditEntry] 处理。
 *
 * 抽成独立 composable 是为了让首屏骨架覆盖层也能画出同一行 —— 骨架期间
 * 上半部分就是真的，只有卡片区域在呼吸。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeCatRow(
    state: HomeState,
    scrollState: ScrollState,
    entries: List<HomeEntry>,
    onEditEntry: (HomeEntry?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeChip(
            label = "热门",
            selected = state.popular,
            onClick = { state.selectPopular() },
            onLongClick = null,
        )
        entries.forEach { entry ->
            val selected = when {
                entry.isGroup -> state.group?.id == entry.id
                !state.popular && state.group == null -> state.cats == entry.mask
                else -> false
            }
            HomeChip(
                label = entry.label,
                selected = selected,
                onClick = {
                    if (entry.isGroup) state.selectGroup(entry) else state.selectCats(entry.mask)
                },
                onLongClick = { onEditEntry(entry) },
            )
        }
        HomeChip(
            label = "＋",
            selected = false,
            onClick = { onEditEntry(null) },
            onLongClick = null,
        )
    }
}

/** 分类行里的单个 chip；[onLongClick] 为 null 时只响应点按（热门、＋）。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .squircleSurface(
                color = if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.surfaceVariant,
                cornerRadius = 12.dp,
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MiuixTheme.textStyles.footnote2,
            color = if (selected) MiuixTheme.colorScheme.onPrimary
            else MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 168.dp),
        )
    }
}

class HomeState(
    private val api: EhApi = createEhApi(),
) {

    private val source = api

    /**
     * 自带作用域：请求不挂在组合上。
     * 否则用户刚进首页就切走，LaunchedEffect 被取消，请求白做。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var inFlight = false

    /** 每个分类/分组独立缓存：切回已加载过的选择直接命中，不重新请求。 */
    private val cache = mutableMapOf<String, List<GalleryUi>>()
    private val cursorByCat = mutableMapOf<String, String?>()
    private val endByCat = mutableMapOf<String, Boolean>()

    var items by mutableStateOf<List<GalleryUi>>(emptyList())
        private set

    var initialLoading by mutableStateOf(true)
        private set

    var refreshing by mutableStateOf(false)
        private set

    var loadingMore by mutableStateOf(false)
        private set

    var endReached by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** 本次列表里被过滤规则隐藏掉的条数。 */
    var hiddenCount by mutableStateOf(0)
        private set

    /** 已经成功加载过当前分类；用于 Tab 切回来时跳过重复请求。 */
    var loadedOnce by mutableStateOf(false)

    /** 当前分类筛选位掩码（保留位）；[HomeCats.ALL] 表示全部。 */
    var cats by mutableStateOf(HomeCats.ALL)
        private set

    /** 当前选中的自定义分组；与「热门」和内置分类三者互斥。 */
    var group by mutableStateOf<HomeEntry?>(null)
        private set

    /** 首页头部是否切到了「热门」（走站点 /popular，而不是首页列表）。 */
    var popular by mutableStateOf(false)
        private set

    private var nextHref: String? = null

    /**
     * 分类 chips 的横向滚动状态。
     *
     * 首屏骨架覆盖层会再画一行同样的 chips 用来对齐骨架，两处共用同一个
     * [ScrollState]，否则「真实行已横向滑动、覆盖层那行还在最左」会叠出两层错位按钮。
     */
    val catRowScroll = ScrollState(0)

    /** 本次首屏从本地缓存恢复了多少条；网络页回来时用它判断要不要合并而不是覆盖。 */
    private var restoredCount = 0

    private val bucket: String
        get() = group?.let { "g:${it.id}" } ?: if (popular) POPULAR_KEY else "cat:$cats"

    /** 缓存键：热门是固定串，分组与分类各有前缀，切 Profile 时同一套前缀一起清。 */
    private fun cacheKey(bucketValue: String): String = when {
        bucketValue == POPULAR_KEY -> GalleryCache.popularKey()
        bucketValue.startsWith("g:") -> GalleryCache.groupKey(bucketValue.removePrefix("g:"))
        else -> GalleryCache.homeKey(bucketValue.removePrefix("cat:").toIntOrNull() ?: HomeCats.ALL)
    }

    fun selectCats(mask: Int) {
        if (!popular && group == null && mask == cats) return
        popular = false
        group = null
        cats = mask
        switchTo("cat:$mask")
    }

    /** 头部「热门」：与分类、分组互斥，切过去时其它 chips 全部淡出选中态。 */
    fun selectPopular() {
        if (popular) return
        popular = true
        group = null
        switchTo(POPULAR_KEY)
    }

    /** 选中一个自定义分组：请求改走站点搜索，标签条件由 [HomeEntry.searchQuery] 拼。 */
    fun selectGroup(entry: HomeEntry) {
        if (group?.id == entry.id) return
        popular = false
        group = entry
        switchTo("g:${entry.id}")
    }

    private fun switchTo(bucketValue: String) {
        error = null
        inFlight = false
        loadingMore = false
        // 命中缓存：瞬间切换，不显示骨架
        val cached = cache[bucketValue]
        if (cached != null) {
            items = cached
            nextHref = cursorByCat[bucketValue]
            endReached = endByCat[bucketValue] ?: false
            loadedOnce = true
            initialLoading = false
            hiddenCount = 0
            return
        }
        items = emptyList()
        endReached = false
        loadedOnce = false
        hiddenCount = 0
        initialLoading = true
        ensureLoaded()
    }

    fun ensureLoaded() {
        if (loadedOnce || inFlight) return
        scope.launch {
            inFlight = true
            try {
                loadFirstPage(refresh = false)
            } finally {
                inFlight = false
            }
        }
    }

    fun refresh() {
        if (refreshing) return
        cache.remove(bucket)
        cursorByCat.remove(bucket)
        endByCat.remove(bucket)
        hiddenCount = 0
        scope.launch { loadFirstPage(refresh = true) }
    }

    fun loadMore() {
        if (loadingMore || refreshing || initialLoading) return
        val cursor = nextHref ?: return
        scope.launch { loadMoreInternal(cursor) }
    }

    fun retry() {
        if (items.isEmpty()) {
            cache.remove(bucket)
            loadedOnce = false
            ensureLoaded()
        } else {
            loadMore()
        }
    }

    /** 富化失败项的后台补课：一直重试到补齐或切走分类，合进列表并落盘。 */
    private fun scheduleEnrichRetry(bucketValue: String, published: List<GalleryUi>, failed: Set<Long>) {
        if (failed.isEmpty()) return
        scope.enrichRetryLoop(source, published, failed, active = { bucket == bucketValue }) { updated ->
            // 补课拿到的标签也可能命中屏蔽规则：合并后再过一遍过滤器。
            items = GalleryFeed.applyRetry(items, updated)
            cache[bucketValue] = items
            // 补全后的列表落盘，下次冷启动直接从缓存秒开（缺的字段由骨架顶着）。
            GalleryCache.saveMerged(cacheKey(bucketValue), items, nextHref)
        }
    }

    private suspend fun loadFirstPage(refresh: Boolean) {
        val cat = cats
        val entry = group
        val bucketAtStart = bucket
        val usePopular = bucketAtStart == POPULAR_KEY
        val key = cacheKey(bucketAtStart)
        // P0：先读本地缓存立即出内容（缺的字段由卡片骨架顶着），再走网络刷新（下拉刷新跳过缓存）。
        // 卡片高度固定、标签区留白，缺标签/评分不会引起二次布局，所以这里不再等“完整”缓存。
        if (!refresh) {
            val cached = GalleryCache.page(key)
            if (cached != null && items.isEmpty() && bucket == bucketAtStart) {
                val cachedFiltered = app.eros.shared.filter.EffectiveFilters.filter(cached.items)
                items = cachedFiltered
                restoredCount = cachedFiltered.size
                nextHref = cached.nextHref
                endReached = cached.nextHref == null
                cache[bucketAtStart] = cachedFiltered
                cursorByCat[bucketAtStart] = cached.nextHref
                endByCat[bucketAtStart] = cached.nextHref == null
                hiddenCount = cached.items.size - cachedFiltered.size
                loadedOnce = true
                initialLoading = false
            }
        }
        if (refresh) refreshing = true else if (items.isEmpty()) initialLoading = true
        error = null
        // 下拉刷新强制走网络；首次/切分类允许命中 10 分钟页面缓存。
        val page = withEhErrors({ error = it }) {
            when {
                entry != null -> source.search(
                    query = entry.searchQuery(),
                    nextHref = null,
                    catsExclude = entry.excludeCats,
                    forceRefresh = refresh,
                )
                usePopular -> source.popular(nextHref = null, forceRefresh = refresh)
                else -> source.frontPage(null, cat, forceRefresh = refresh)
            }
        }
        if (page != null) {
            // 富化 + 过滤的固定顺序收在 GalleryFeed 里：标签只有富化后才拿得到。
            val feed = GalleryFeed.enrich(source, page.items)
            val fresh = feed.items
            // 缓存里往往已经有好多页（滚动过的历史）。这里把新页合并进已有列表，
            // 不能直接覆盖，否则秒开的长列表会在刷新回来的瞬间“缩水”成一页。
            val merged = if (restoredCount > fresh.size) GalleryFeed.merge(items, fresh) else fresh
            restoredCount = 0
            hiddenCount = feed.hidden
            cache[bucketAtStart] = merged
            cursorByCat[bucketAtStart] = page.nextHref
            endByCat[bucketAtStart] = page.nextHref == null
            if (bucket == bucketAtStart) {
                items = merged
                nextHref = page.nextHref
                endReached = page.nextHref == null
                loadedOnce = true
                // 这批没补齐的项交给后台补课，不再在列表顶部挂一条可点提示。
                scheduleEnrichRetry(bucketAtStart, merged, feed.failed)
            }
            GalleryCache.saveMerged(key, merged, page.nextHref)
            AppLog.i("HomeState", "${if (refresh) "refresh" else "initial"} bucket=$bucketAtStart got=${page.items.size} loaded=${fresh.size} hidden=${feed.hidden} next=${page.nextHref != null}")
        } else {
            AppLog.w("HomeState", "FAILED: $error")
        }
        // 切分类后旧请求可能才回来：只允许当前分类清 loading 状态，
        // 否则会把新分类的骨架屏提前关掉，看起来像"切换没生效"。
        if (bucket == bucketAtStart) {
            if (refresh) refreshing = false else initialLoading = false
        }
    }

    private suspend fun loadMoreInternal(firstCursor: String) {
        loadingMore = true
        AppLog.d("HomeState", "loadMore start cursor=${firstCursor.take(60)}")
        val cat = cats
        val entry = group
        val bucketAtStart = bucket
        val usePopular = bucketAtStart == POPULAR_KEY
        var cursor: String? = firstCursor
        var rounds = 0
        while (cursor != null && rounds < 3) {
            rounds++
            val page = withEhErrors({ error = it }) {
                when {
                    entry != null -> source.search(
                        query = entry.searchQuery(),
                        nextHref = cursor,
                        catsExclude = entry.excludeCats,
                    )
                    usePopular -> source.popular(nextHref = cursor)
                    else -> source.frontPage(cursor, cat)
                }
            }
            if (page == null) {
                AppLog.w("HomeState", "loadMore FAILED: $error")
                break
            }
            // 分类已经切走：这一页属于旧分类，丢弃，不能拼到新分类列表里。
            if (bucket != bucketAtStart) {
                AppLog.d("HomeState", "discard stale loadMore bucket=$bucketAtStart current=$bucket")
                break
            }
            if (page.items.isEmpty()) {
                endReached = true
                nextHref = null
                break
            }
            val feed = GalleryFeed.enrich(source, page.items)
            val merged = feed.items
            hiddenCount += feed.hidden
            val seen = HashSet<Long>(items.size * 2)
            items.forEach { seen.add(it.gid) }
            items = items + merged.filter { seen.add(it.gid) }
            nextHref = page.nextHref
            endReached = page.nextHref == null
            if (bucket == bucketAtStart) {
                cache[bucketAtStart] = items
                cursorByCat[bucketAtStart] = page.nextHref
                endByCat[bucketAtStart] = page.nextHref == null
                scheduleEnrichRetry(bucketAtStart, merged, feed.failed)
            }
            AppLog.i("HomeState", "loadMore loaded=${merged.size} hidden=${feed.hidden} total=${items.size} next=${page.nextHref != null}")
            // 整页都被隐藏：继续取下一页，避免列表空着不动。
            if (merged.isNotEmpty() || page.nextHref == null) break
            cursor = page.nextHref
        }
        if (bucket == bucketAtStart) GalleryCache.saveMerged(cacheKey(bucketAtStart), items, nextHref)
        loadingMore = false
    }
}

@Composable
fun HomeScreen(
    state: HomeState,
    gridState: LazyGridState,
    onGalleryClick: (GalleryUi) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAccount: () -> Unit,
    onEditHomeEntry: (String?) -> Unit = {},
) {
    val barInset = LocalAppBottomBarInset.current
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
    // 自定义分组是设置里的列表：删掉当前选中的那个之后要退回「全部」，否则会停在一个不存在的选择上。
    val homeEntries = AppSettings.homeEntries
    LaunchedEffect(homeEntries) {
        val current = state.group
        if (current != null && homeEntries.none { it.isGroup && it.id == current.id }) {
            state.selectCats(HomeCats.ALL)
        }
    }
    val onEditEntry: (HomeEntry?) -> Unit = { entry -> onEditHomeEntry(entry?.key) }
    CoverPrefetchEffect(gridState, state.items)
                DetailPrefetchEffect(gridState, state.items)

    val nearBottom by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            // 提前到 70% 就开始抓下一页：E-H 的列表请求偶尔会卡 10s+，
            // 等真正滑到底再抓会让用户一直看 footer 转圈。
            val triggerAt = (state.items.size * 0.7f).toInt().coerceAtLeast(1)
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= triggerAt && !state.loadingMore
        }
    }

    // 只响应用户滚动：网格被 LazyGrid 锚定/恢复位置时也会「停在底部」，
    // 仅靠可见性判断会在无操作时连续翻页。
    var userScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }.collect { if (it) userScrolled = true }
    }
    LaunchedEffect(nearBottom, state.items.size, state.endReached, userScrolled) {
        if (nearBottom && userScrolled) {
            userScrolled = false
            state.loadMore()
        }
    }

    ErosPageScaffold(
        title = "首页",
        navigationIcon = { AccountEntry(onOpenAccount) },
        actions = { HistoryAndSearchEntries(onOpenHistory, onOpenSearch) },
    ) { innerPadding, pageScrollBehavior, pageBackdrop ->
        PagedGalleryGrid(
            items = state.items,
            gridState = gridState,
            isRefreshing = state.refreshing,
            initialLoading = state.initialLoading,
            loadingMore = state.loadingMore,
            endReached = state.endReached,
            error = state.error,
            innerPadding = innerPadding,
            pageScrollBehavior = pageScrollBehavior,
            onRefresh = { state.refresh() },
            onRetry = { state.retry() },
            onItemClick = onGalleryClick,
            emptyTitle = "没有数据",
            emptyDescription = "下拉刷新，或切换分类试试",
            emptyHeight = 220.dp,
            header = { HomeCatRow(state, state.catRowScroll, homeEntries, onEditEntry) },
        )
    }
}
