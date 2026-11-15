// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.collectAsState

import app.eros.shared.data.GalleryTitles
import app.eros.shared.download.DownloadStatus

import app.eros.shared.download.DownloadManager

import app.eros.shared.ui.ErosAppBottomBar
import app.eros.shared.ui.ErosNavigationRail
import app.eros.shared.ui.OverlayState
import app.eros.shared.ui.rememberUseNavigationRail
import app.eros.shared.ui.ReadStore

import app.eros.shared.ui.TopListState

import app.eros.shared.ui.TopListScreen

import app.eros.shared.ui.FilterScreen

import app.eros.shared.ui.NetLogScreen

import app.eros.shared.ui.WatchedState

import app.eros.shared.ui.WatchedScreen

import androidx.compose.runtime.movableContentOf



import androidx.compose.foundation.layout.statusBarsPadding

import app.eros.shared.ui.FPSMonitor

import app.eros.shared.ui.barBlurColors

import app.eros.shared.ui.rememberBarBackdrop

import app.eros.shared.ui.AccountScreen
import app.eros.shared.ui.AppStateHolder

import app.eros.shared.ui.CommentsScreen
import app.eros.shared.ui.CommentsState
import app.eros.shared.ui.HistoryScreen
import app.eros.shared.ui.DownloadScreen
import app.eros.shared.ui.AppTabs
import app.eros.shared.ui.BottomTab
import app.eros.shared.ui.ErosTopBar

import kotlinx.coroutines.job

import kotlinx.coroutines.CoroutineScope

import androidx.compose.foundation.pager.PagerState

import kotlinx.coroutines.launch

import kotlinx.coroutines.Job

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.mutableIntStateOf

import top.yukonga.miuix.kmp.utils.pagerGestureOverride
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils
import androidx.compose.foundation.gestures.Orientation

import top.yukonga.miuix.kmp.utils.springAnimateToPage

import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec

import androidx.compose.foundation.pager.PagerDefaults

import app.eros.shared.ui.currentBarBlurStyle

import app.eros.shared.ui.BarBlurStyle

import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.foundation.pager.HorizontalPager

import app.eros.shared.system.setPlainBackInterceptor
import app.eros.shared.system.showSystemToast

import app.eros.shared.PlatformBackHandler

import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius

import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode

import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects

import top.yukonga.miuix.kmp.basic.TopAppBar

import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

import androidx.compose.ui.zIndex

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import androidx.compose.foundation.isSystemInDarkTheme

import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode

import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem

import top.yukonga.miuix.kmp.basic.FloatingNavigationBar

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack

import top.yukonga.miuix.kmp.basic.HorizontalDivider


import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.eros.shared.auth.applyExHentai
import app.eros.shared.auth.restoreAuth
import app.eros.shared.diag.installDiagnostics
import app.eros.shared.diag.pendingCrashSummary
import app.eros.shared.diag.setNetLogEnabled
import app.eros.shared.proxy.applyProxy
import com.hippo.ehviewer.net.EhHttp
import app.eros.shared.settings.AppSettings
import app.eros.shared.ui.DetailScreen
import app.eros.shared.ui.FavouritesScreen
import app.eros.shared.ui.FavouritesState
import app.eros.shared.ui.DetailState
import app.eros.shared.ui.HomeScreen
import app.eros.shared.ui.HomeChipEditScreen
import app.eros.shared.ui.HomeState
import app.eros.shared.reader.ReaderSession
import app.eros.shared.ui.SearchScreen
import app.eros.shared.ui.reader.ReaderScreen
import app.eros.shared.ui.SearchState
import app.eros.shared.ui.SettingsScreen
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

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

@Serializable
private sealed interface Route : NavKey {
    @Serializable
    data object Tabs : Route

    @Serializable
    data class Detail(val gid: Long, val token: String) : Route

    @Serializable
    data class Reader(
        val gid: Long,
        val token: String,
        val total: Int,
        val startPage: Int = -1,
    ) : Route

    @Serializable
    data class Comments(val gid: Long, val token: String) : Route

    @Serializable
    data object Search : Route

    @Serializable
    data object Account : Route

    @Serializable
    data object Watched : Route

    @Serializable
    data object NetLog : Route

    @Serializable
    data object Filter : Route

    /** 首页分类行条目的新建/编辑页；[key] 为空表示新建分组。 */
    data class HomeChipEdit(val key: String? = null) : Route

    @Serializable
    data object History : Route
}

/** pager 页面需要的回调与尺寸（movableContentOf 最多 4 个参数，这里打包成一个）。 */
private data class TabPageParams(
    val onSearch: () -> Unit,
    val onOpenGallery: (Long, String) -> Unit,
    val onOpenReader: (Long, String, Int) -> Unit,
    val bottom: androidx.compose.ui.unit.Dp,
    val onOpenWatched: () -> Unit,
    val onOpenNetLog: () -> Unit,
    val onOpenFilter: () -> Unit,
    val onOpenAccount: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onEditHomeEntry: (String?) -> Unit,
)

@Composable
fun App() {
    SetupImageLoader()
    remember { AppSettings.load() }
    // 主题模式变化时重建 controller（miuix 的 colorSchemeMode 是只读属性）
    val themeMode = AppSettings.themeMode
    val controller = remember(themeMode) {
        ThemeController(ColorSchemeMode.entries.getOrElse(themeMode) { ColorSchemeMode.System })
    }
    MiuixTheme(controller = controller) {
        // 启动时按持久化设置恢复状态
        DisposableEffect(Unit) {
            onDispose { AppSettings.flush() }
        }
        LaunchedEffect(Unit) {
            // 诊断能力：接管 println 的旁路 + 崩溃落盘，必须在任何业务日志之前装好。
            installDiagnostics()
            setNetLogEnabled(AppSettings.netLogEnabled)
            // 标签库与下面的网络/登录恢复并行预热：它只占一个 IO 线程几百毫秒，
            // 启动时先开始，卡片首帧就能拿到中文标签，不再「先英文、再变中文」。
            app.eros.shared.tags.TagDatabase.warmUpIn(this)
            // 再恢复网络与登录态（首屏请求依赖它们）
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                applyProxy(AppSettings.proxyType, AppSettings.proxyHost, AppSettings.proxyPort)
                EhHttp.update(
            maxRequestsPerHost = AppSettings.netMaxPerHost,
            maxRequestsPerSecond = AppSettings.maxRequestsPerSecond,
            domainFronting = AppSettings.domainFronting,
        )
                restoreAuth()
                // 未登录只能用表站：里站必须带 Cookie 才能访问，残留的「使用里站」
                // 会让首屏直接拿到 sad panda，所以这里把它复位成表站。
                if (!app.eros.shared.auth.isSignedIn() && AppSettings.exHentai) {
                    AppSettings.exHentai = false
                }
                applyExHentai(AppSettings.exHentai)
                if (app.eros.shared.auth.isSignedIn()) {
                    app.eros.shared.filter.HiddenTagStore.refresh()
                }
                app.eros.shared.data.cache.GalleryCache.pruneIfNeeded()
                if (AppSettings.tagCountEnabled) {
                    runCatching { app.eros.shared.data.updateTagCounts(force = false) }
                }
            }
            println("[App] ready")
            // 上次是崩溃退出的：提示一句，引导去「设置 → 关于 → 导出日志」；导出后才会清标记。
            pendingCrashSummary()?.let {
                showSystemToast("上次启动崩溃过，可在设置 → 关于 导出日志")
            }
        }
        // 栈式导航：返回一定是回到上一层
        val backStack = rememberNavBackStack<Route>(Route.Tabs)
        // 同值路由已经在栈里时不压新的一层，而是回退到已有的那一层：
        // NavDisplay 要求栈上每层的 contentKey 唯一，否则会直接抛
        // 「Duplicate contentKey on the back stack」（例如 搜索 → 详情 → 点上传者再开搜索）。
        val push: (Route) -> Unit = { route ->
            val existing = backStack.indexOfLast { it == route }
            if (existing < 0) {
                backStack.add(route)
            } else {
                while (backStack.size - 1 > existing) backStack.removeLastOrNull()
            }
        }
        val pop: () -> Unit = { backStack.removeLastOrNull() }
        // tab 下标放进程级：Activity 重建（回桌面再回来）也停在同一页
        val tabIndex = AppStateHolder.selectedTab

        // 页面状态统一放这里：进出页面不重建，一个启动周期内只加载一次。
        // 进程级持有：Activity 重建（回桌面再回来）不会丢列表数据
        val store = AppStateHolder.store
        val homeState = AppStateHolder.homeState
        val favState = AppStateHolder.favouritesState
        val searchState = AppStateHolder.searchState
        val openTag: (String) -> Unit = { q -> searchState.search(q); push(Route.Search) }
        val openUploader: (String) -> Unit = { u -> searchState.searchUploader(u); push(Route.Search) }
        val homeGridState = AppStateHolder.homeGridState
        val topListState = AppStateHolder.topListState
        val rankGridState = AppStateHolder.rankGridState
        // 不用 rememberLazyGridState（它会跨进程恢复旧偏移）；App 级 remember 足够保持会话内位置
        val favGridState = AppStateHolder.favGridState

        // 预测式返回已固定关闭：统一用系统级回调接管，系统不再索取跟手进度
        LaunchedEffect(backStack.size) {
            setPlainBackInterceptor(true) {
                if (backStack.size > 1) {
                    pop()
                    true
                } else {
                    false   // 栈底交给 Activity 退出
                }
            }
        }

        // 官方示例的转场效果：跟随设备屏幕圆角、轻微调暗、底部露出宿主背景
        // 侧滑返回已固定关闭
        val swipeDismiss = NavSwipeDirection.None
        val navEffects = NavDisplayEffects(
            cornerClipRadius = rememberNavSystemCornerRadius(),
            cornerClipMode = NavCornerClipMode.All,
            dimAmount = 0.28f,
            backdropColor = MiuixTheme.colorScheme.background,
        )

        // 统一底栏：放在 NavDisplay 之外，Tabs↔Detail 共用同一个实例，
        // 只做内容淡变 + 宽度平滑过渡，位置/高度/模糊保持不变。
        val appBarBackdrop = rememberBarBackdrop()
        val currentRoute = backStack.lastOrNull()
        val detailRoute = currentRoute as? Route.Detail
        val activeDetail: DetailState? = detailRoute?.let { r ->
            remember(r.gid, r.token) {
                store.get("detail:${r.gid}:${r.token}") { DetailState(r.gid, r.token) }
            }
        }
        // 收藏夹弹层打开时隐藏底栏，避免底栏盖在弹层上方
        val showAppBar = (currentRoute is Route.Tabs || detailRoute != null) &&
            !OverlayState.anyOpen

        // 宽屏（≥600dp）把导航搬到左侧 rail：Tabs 与详情页共用，详情动作也进 rail
        val railAllowed = currentRoute is Route.Tabs || detailRoute != null
        val useNavigationRail = railAllowed && rememberUseNavigationRail()

        // 详情动作由底栏与 rail 共用，状态与回调只算一次
        val downloadTasks by DownloadManager.tasks.collectAsState()
        val detailTask = activeDetail?.let { st -> downloadTasks.firstOrNull { it.gid == st.gid } }
        val readProgress = activeDetail?.let { st -> ReadStore.progressOf(st.gid) }
        val detailDownloadLabel = when {
            detailTask == null -> "下载"
            detailTask.status == DownloadStatus.DONE -> "已下载"
            else -> "${(detailTask.progress * 100).toInt()}%"
        }
        val onDetailDownloadAction: () -> Unit = {
            val st = activeDetail
            val d = st?.detail
            if (st != null && d != null && detailTask == null) {
                DownloadManager.enqueue(
                    gid = d.gid,
                    token = st.token,
                    title = GalleryTitles.primary(d.title, d.titleJpn),
                    titleJpn = d.titleJpn,
                    totalPages = d.pages,
                    thumb = d.thumb,
                    uploader = d.uploader,
                    artist = d.tags.firstOrNull { it.namespace == "artist" }?.tags?.firstOrNull(),
                    category = d.category,
                )
                showSystemToast("已加入下载队列")
            }
        }
        val onDetailReadAction: () -> Unit = {
            val st = activeDetail
            if (st != null) push(Route.Reader(st.gid, st.token, st.detail?.pages ?: 0))
        }

        Box(Modifier.fillMaxSize()) {

        // 宽屏：左侧 rail + 右侧内容区；底栏只对齐右 pane 底部
        Row(Modifier.fillMaxSize()) {

        if (useNavigationRail) {
            ErosNavigationRail(
                isDetail = detailRoute != null,
                selectedTab = tabIndex,
                detailEnabled = activeDetail?.detail != null,
                detailFavoriteCount = activeDetail?.favoriteCount ?: 0,
                detailReadPage = readProgress?.first?.plus(1),
                detailDownloadLabel = detailDownloadLabel,
                detailDownloadActive = detailTask != null,
                onSelectTab = { index ->
                    AppStateHolder.selectedTab = index
                    while (backStack.size > 1) backStack.removeLastOrNull()
                },
                onDetailFavorite = { activeDetail?.openFavSheet() },
                onDetailDownload = onDetailDownloadAction,
                onDetailRead = onDetailReadAction,
                // 场景切换时 NavDisplay 会画上一页的底图，rail 必须画在它之上
                modifier = Modifier.zIndex(1f),
            )
        }

        Box(Modifier.weight(1f)) {

        NavDisplay(
            modifier = Modifier
                .fillMaxSize()
                .then(if (appBarBackdrop != null) Modifier.layerBackdrop(appBarBackdrop) else Modifier),
            backStack = backStack,
            onBack = { pop() },
            effects = navEffects,
        ) {
            entry<Route.Tabs> {
                Tabs(
                    selected = tabIndex,
                    onSelect = { AppStateHolder.selectedTab = it },
                    homeState = homeState,
                    favState = favState,
                    homeGridState = homeGridState,
                    topListState = topListState,
                    rankGridState = rankGridState,
                    favGridState = favGridState,
                    onOpenSearch = { push(Route.Search) },
                    onOpenDetail = { gid, token -> push(Route.Detail(gid, token)) },
                    onOpenReader = { gid, token, total -> push(Route.Reader(gid, token, total)) },
                    onOpenWatched = { push(Route.Watched) },
                    onOpenNetLog = { push(Route.NetLog) },
                    onOpenFilter = { push(Route.Filter) },
                    onOpenAccount = { push(Route.Account) },
                    onOpenHistory = { push(Route.History) },
                    onEditHomeEntry = { key -> push(Route.HomeChipEdit(key)) },
                )
            }

            entry<Route.Detail>(swipeDismiss = swipeDismiss) { current ->
                val state = remember(current.gid, current.token) {
                    store.get("detail:${current.gid}:${current.token}") {
                        DetailState(current.gid, current.token)
                    }
                }
                DetailScreen(
                    state = state,
                    onBack = { pop() },
                    onRead = {
                        val pages = state.detail?.pages ?: 0
                        if (pages > 0) push(Route.Reader(current.gid, current.token, pages))
                    },
                    onReadAt = { page ->
                        val pages = state.detail?.pages ?: 0
                        // 详情还没加载完（页数未知）时不进阅读器，否则会当成 0 页的空画廊。
                        if (pages > 0) {
                            push(Route.Reader(current.gid, current.token, pages, startPage = page))
                        }
                    },
                    onOpenComments = { push(Route.Comments(current.gid, current.token)) },
                    onSearchTag = openTag,
                    onSearchUploader = openUploader,
                    onOpenGallery = { ref -> push(Route.Detail(ref.gid, ref.token)) },
                )
            }
            entry<Route.Watched>(swipeDismiss = swipeDismiss) {
                val state = remember { store.get("watched") { WatchedState() } }
                WatchedScreen(
                    state = state,
                    onBack = { pop() },
                    onSearchTag = { tag -> searchState.search(tag); push(Route.Search) },
                )
            }

            entry<Route.NetLog>(swipeDismiss = swipeDismiss) {
                NetLogScreen(onBack = { pop() })
            }

            entry<Route.Filter>(swipeDismiss = swipeDismiss) {
                FilterScreen(onBack = { pop() })
            }

            entry<Route.HomeChipEdit>(swipeDismiss = swipeDismiss) { current ->
                HomeChipEditScreen(entryKey = current.key, onBack = { pop() })
            }

            entry<Route.Account>(swipeDismiss = swipeDismiss) {
                AccountScreen(onBack = { pop() })
            }

            entry<Route.History>(swipeDismiss = swipeDismiss) {
                HistoryScreen(
                    onBack = { pop() },
                    // 点历史卡片先进画廊详情（不再直接跳阅读器）：
                    // 详情里能看到完整信息与下载状态，再决定是阅读还是继续上次的页。
                    onOpen = { e -> push(Route.Detail(e.gid, e.token)) },
                    // 卡片右下角的「阅读」直接续读：把记录里的页码当作起始页。
                    onRead = { e -> push(Route.Reader(e.gid, e.token, e.pages, startPage = e.page)) },
                )
            }

            entry<Route.Search>(swipeDismiss = swipeDismiss) {
                SearchScreen(
                    state = searchState,
                    onBack = { pop() },
                    onGalleryClick = { push(Route.Detail(it.gid, it.token)) },
                    onOpenGallery = { ref -> push(Route.Detail(ref.gid, ref.token)) },
                )
            }

            entry<Route.Comments>(swipeDismiss = swipeDismiss) { current ->
                val state = remember(current.gid, current.token) {
                    store.get("comments:${current.gid}:${current.token}") {
                        CommentsState(current.gid, current.token)
                    }
                }
                CommentsScreen(
                    state = state,
                    onBack = { pop() },
                    onOpenGallery = { ref -> push(Route.Detail(ref.gid, ref.token)) },
                )
            }

            entry<Route.Reader> { current ->
                val session = remember(current.gid, current.token) {
                    store.get("reader:${current.gid}:${current.token}") {
                        ReaderSession(current.gid, current.token, current.total, current.startPage)
                    }
                }
                // 会话是按画廊缓存的：退出阅读器后再点预览图，拿到的是同一个实例，
                // 它的 ensureLoaded 早就跑过、构造参数里的 startPage 不会再生效。
                // 所以这里显式把会话挪到这次要求的页（没指定页时 -1，保持原地续读）。
                LaunchedEffect(current.gid, current.token, current.startPage) {
                    if (current.startPage >= 0) session.openAt(current.startPage)
                }
                ReaderScreen(
                    session = session,
                    onBack = { pop() },
                )
            }
        }
        }

        }

        // 统一底栏（NavDisplay 之外）：Tabs 与 Detail 共用同一实例，只切换内容
        AnimatedVisibility(
            visible = showAppBar && !useNavigationRail,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 2 },
        ) {
            ErosAppBottomBar(
                    isDetail = detailRoute != null,
                    selectedTab = tabIndex,
                    onSelectTab = { index ->
                        AppStateHolder.selectedTab = index
                        while (backStack.size > 1) backStack.removeLastOrNull()
                    },
                    detailEnabled = activeDetail?.detail != null,
                    detailFavorited = activeDetail?.favorited ?: false,
                    detailFavoriteCount = activeDetail?.favoriteCount ?: 0,
                    detailReadPage = readProgress?.first?.plus(1),
                    detailReadStarted = readProgress != null,
                    detailDownloadLabel = detailDownloadLabel,
                    detailDownloadActive = detailTask != null,
                    onDetailFavorite = { activeDetail?.openFavSheet() },
                    onDetailDownload = onDetailDownloadAction,
                    onDetailRead = onDetailReadAction,
                    backdrop = appBarBackdrop,
                )
        }

        // 帧率浮层：official example 的 FPSMonitor
        if (AppSettings.showFps) {
            FPSMonitor(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 4.dp),
            )
        }

        // 给 NavDisplay push 出来的路由（历史 / 详情等）补一个 popup 宿主：
        // 这些页面的 miuix Scaffold 宿主会被 NavDisplay 的内容盖住，
        // 导致 OverlayDialog / OverlayListPopup「点了没反应」。放在整页最上层即可。
        MiuixPopupUtils.MiuixPopupHost()
        }
    }
}

@Composable
private fun Tabs(
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
    // 1 悬浮、2 iOS 风格都是悬浮底栏，内容需额外让出底部空间
    val floatingBar = AppSettings.navBarStyle != 0
    // 宽屏下导航在左侧 rail 里，页面底部不再需要给底栏留位置
    val barBottomInset = if (rememberUseNavigationRail()) 0.dp else 96.dp
    // 底栏模糊用的 backdrop（捕获 pager 区域），与各页顶栏的 backdrop 互不影响
    val barBackdrop = rememberBarBackdrop()
    // 只有「下载」这个占位页还用 Tabs 层提供的顶栏资源
    val downloadBackdrop = rememberBarBackdrop()
    val downloadScrollBehavior = MiuixScrollBehavior()

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

            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (barBackdrop != null) Modifier.layerBackdrop(barBackdrop) else Modifier),
            ) {
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
                            bottomInset = p.bottom,
                            onEditHomeEntry = p.onEditHomeEntry,
                        )

                        1 -> TopListScreen(
                            state = topListState,
                            gridState = rankGridState,
                            onGalleryClick = { g -> p.onOpenGallery(g.gid, g.token) },
                            onOpenSearch = p.onSearch,
                            onOpenHistory = p.onOpenHistory,
                            onOpenAccount = p.onOpenAccount,
                            bottomInset = p.bottom,
                        )

                        2 -> FavouritesScreen(
                            state = AppStateHolder.favouritesState,
                            gridState = AppStateHolder.favGridState,
                            onGalleryClick = { g -> p.onOpenGallery(g.gid, g.token) },
                            onOpenSearch = p.onSearch,
                            onOpenHistory = p.onOpenHistory,
                            onOpenAccount = p.onOpenAccount,
                            bottomInset = p.bottom,
                        )

                        3 -> DownloadScreen(
                            onOpenDetail = { gid, token, _ -> p.onOpenGallery(gid, token) },
                            onOpenReader = { gid, token, total -> p.onOpenReader(gid, token, total) },
                            onOpenSearch = p.onSearch,
                            onOpenHistory = p.onOpenHistory,
                            onOpenAccount = p.onOpenAccount,
                            bottomInset = p.bottom,
                        )

                        else -> SettingsScreen(
                            onOpenSearch = p.onSearch,
                            onOpenHistory = p.onOpenHistory,
                            onOpenAccount = p.onOpenAccount,
                            onOpenWatched = p.onOpenWatched,
                            onOpenNetLog = p.onOpenNetLog,
                            onOpenFilter = p.onOpenFilter,
                            bottomInset = p.bottom,
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
                        bottom = barBottomInset,
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

