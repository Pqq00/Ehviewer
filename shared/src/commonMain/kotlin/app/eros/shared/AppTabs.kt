// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

import app.eros.shared.ui.TopListState
import app.eros.shared.ui.TopListScreen
import androidx.compose.runtime.movableContentOf
import app.eros.shared.ui.AppStateHolder
import app.eros.shared.ui.DownloadScreen
import app.eros.shared.ui.AppTabs
import kotlinx.coroutines.job
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.gestures.Orientation
import top.yukonga.miuix.kmp.utils.springAnimateToPage
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.eros.shared.ui.FavouritesScreen
import app.eros.shared.ui.FavouritesState
import app.eros.shared.ui.HomeScreen
import app.eros.shared.ui.HomeState
import app.eros.shared.ui.SettingsScreen
import top.yukonga.miuix.kmp.basic.Scaffold

/**
 * 与官方 `MainPagerState` 同款的分页状态机：
 *  - `selectedPage` 是"目标页"，点导航项时**立即**更新（导航栏高亮不等动画）
 *  - `isNavigating` 期间忽略 pager.currentPage 的回流，避免动画被自己打断
 *  - `animateToPage` 会取消上一次动画，所以连续点击是"改向"而不是排队
 */
private class TabPagerState(
    val pagerState: PagerState,
    private val scope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(target: Int) {
        if (target == selectedPage) return
        navJob?.cancel()
        selectedPage = target
        isNavigating = true
        navJob = scope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.springAnimateToPage(target)
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != target) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
private fun rememberTabPagerState(
    pagerState: PagerState,
    scope: CoroutineScope = rememberCoroutineScope(),
): TabPagerState = remember(pagerState, scope) { TabPagerState(pagerState, scope) }


/** pager 页面需要的回调与尺寸（movableContentOf 最多 4 个参数，这里打包成一个）。 */
private data class TabPageParams(
    val onSearch: () -> Unit,
    val onOpenGallery: (Long, String) -> Unit,
    val onOpenReader: (Long, String, Int) -> Unit,
    val onOpenWatched: () -> Unit,
    val onOpenNetLog: () -> Unit,
    val onOpenFilter: () -> Unit,
    val onOpenAccount: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onEditHomeEntry: (String?) -> Unit,
)

@Composable
internal fun Tabs(
    selected: Int,
    onSelect: (Int) -> Unit,
    homeState: HomeState,
    favState: FavouritesState,
    homeGridState: LazyGridState,
    topListState: TopListState,
    rankGridState: LazyGridState,
    favGridState: LazyGridState,
    onOpenDetail: (Long, String) -> Unit,
    onOpenReader: (Long, String, Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenWatched: () -> Unit,
    onOpenNetLog: () -> Unit,
    onOpenFilter: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenHistory: () -> Unit,
    onEditHomeEntry: (String?) -> Unit,
) {
    val current = selected

    Scaffold(
        contentWindowInsets = WindowInsets(0),
    ) { _: PaddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 页面切换用 Pager（官方示例同样是 Pager 承载 tab 内容），带滑动动画
            val tabPagerState = rememberPagerState(
                initialPage = current,
                pageCount = { AppTabs.size },
            )
            val tabPager = rememberTabPagerState(tabPagerState)

            // 点导航项 → 记录目标页并启动弹簧动画（可被打断/改向）
            LaunchedEffect(current) { tabPager.animateToPage(current) }
            // 手滑结束 → 把 pager 的真实页同步回目标页
            LaunchedEffect(tabPagerState.currentPage) { tabPager.syncPage() }
            // 目标页变化 → 同步给 App 层（tabIndex 持久化用）
            LaunchedEffect(tabPager.selectedPage) {
                if (tabPager.selectedPage != current) onSelect(tabPager.selectedPage)
            }

            val tabFlingBehavior = PagerDefaults.flingBehavior(
                state = tabPagerState,
                snapAnimationSpec = PagerNavigationSpringSpec,
            )
            val tabNestedConnection = PagerDefaults.pageNestedScrollConnection(
                tabPagerState,
                Orientation.Horizontal,
            )

            Box(Modifier.fillMaxSize()) {
            // 官方 AppPager 的做法：页面内容用 movableContentOf 固定身份，
            // pager 每帧重组时跳过页面内部重组，只做布局/绘制。
            val tabPageContent = remember {
                movableContentOf<Int, TabPageParams> { page, p ->
                    when (page) {
                        0 -> HomeScreen(
                            state = AppStateHolder.homeState,
                            gridState = AppStateHolder.homeGridState,
                            onGalleryClick = { g -> p.onOpenGallery(g.gid, g.token) },
                            onOpenSearch = p.onSearch,
                            onOpenAccount = p.onOpenAccount,
                            onOpenHistory = p.onOpenHistory,
                            onEditHomeEntry = p.onEditHomeEntry,
                        )

                        1 -> TopListScreen(
                            state = topListState,
                            gridState = rankGridState,
                            onGalleryClick = { g -> p.onOpenGallery(g.gid, g.token) },
                            onOpenSearch = p.onSearch,
                            onOpenHistory = p.onOpenHistory,
                            onOpenAccount = p.onOpenAccount,
                        )

                        2 -> FavouritesScreen(
                            state = AppStateHolder.favouritesState,
                            gridState = AppStateHolder.favGridState,
                            onGalleryClick = { g -> p.onOpenGallery(g.gid, g.token) },
                            onOpenSearch = p.onSearch,
                            onOpenHistory = p.onOpenHistory,
                            onOpenAccount = p.onOpenAccount,
                        )

                        3 -> DownloadScreen(
                            onOpenDetail = { gid, token, _ -> p.onOpenGallery(gid, token) },
                            onOpenReader = { gid, token, total -> p.onOpenReader(gid, token, total) },
                            onOpenSearch = p.onSearch,
                            onOpenHistory = p.onOpenHistory,
                            onOpenAccount = p.onOpenAccount,
                        )

                        else -> SettingsScreen(
                            onOpenSearch = p.onSearch,
                            onOpenHistory = p.onOpenHistory,
                            onOpenAccount = p.onOpenAccount,
                            onOpenWatched = p.onOpenWatched,
                            onOpenNetLog = p.onOpenNetLog,
                            onOpenFilter = p.onOpenFilter,
                        )
                    }
                }
            }

            HorizontalPager(
                state = tabPagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
                flingBehavior = tabFlingBehavior,
                key = { it },
                beyondViewportPageCount = 0,
            ) { page ->
                tabPageContent(
                    page,
                    TabPageParams(
                        onSearch = onOpenSearch,
                        onOpenGallery = onOpenDetail,
                        onOpenReader = onOpenReader,
                        onOpenWatched = onOpenWatched,
                        onOpenNetLog = onOpenNetLog,
                        onOpenFilter = onOpenFilter,
                        onOpenAccount = onOpenAccount,
                        onOpenHistory = onOpenHistory,
                        onEditHomeEntry = onEditHomeEntry,
                    ),
                )
            }
            }
        }
    }
}
