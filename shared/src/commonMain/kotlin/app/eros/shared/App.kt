// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared


import app.eros.shared.navigation.Route
import app.eros.shared.navigation.rememberErosNavigator
import app.eros.shared.ui.chrome.ErosAppChrome
import app.eros.shared.ui.chrome.ErosSearchOverlay
import app.eros.shared.ui.chrome.LocalSearchBarAlpha
import app.eros.shared.ui.chrome.LocalSearchModeExpanded
import app.eros.shared.ui.chrome.BarDestination
import app.eros.shared.ui.chrome.ErosBottomBarHost
import app.eros.shared.ui.chrome.ErosChromeRail
import app.eros.shared.ui.chrome.rememberDetailActions


import app.eros.shared.ui.OverlayState
import app.eros.shared.ui.rememberUseNavigationRail

import com.hippo.ehviewer.util.AppLog


import app.eros.shared.ui.FilterScreen

import app.eros.shared.ui.NetLogScreen

import app.eros.shared.ui.WatchedState

import app.eros.shared.ui.WatchedScreen




import androidx.compose.foundation.layout.statusBarsPadding

import app.eros.shared.ui.FPSMonitor



import app.eros.shared.ui.AccountScreen
import app.eros.shared.ui.AppStateHolder

import app.eros.shared.ui.CommentsScreen
import app.eros.shared.ui.CommentsState
import app.eros.shared.ui.HistoryScreen









import top.yukonga.miuix.kmp.glass.rememberGlassSearchBarAlpha







import app.eros.shared.system.setPlainBackInterceptor
import app.eros.shared.system.showSystemToast


import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius

import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode

import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavTransitions








import top.yukonga.miuix.kmp.nav.core.NavDisplay



import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.eros.shared.auth.applyExHentai
import app.eros.shared.auth.restoreAuth
import app.eros.shared.diag.installDiagnostics
import app.eros.shared.diag.pendingCrashSummary
import app.eros.shared.diag.setNetLogEnabled
import app.eros.shared.proxy.applyProxy
import com.hippo.ehviewer.net.EhHttp
import app.eros.shared.settings.AppSettings
import app.eros.shared.ui.detail.DetailScreen
import app.eros.shared.ui.detail.DetailState
import app.eros.shared.ui.HomeChipEditScreen
import app.eros.shared.reader.ReaderSession
import app.eros.shared.ui.reader.ReaderScreen
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController


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
            AppLog.i("App", "ready")
            // 上次是崩溃退出的：提示一句，引导去「设置 → 关于 → 导出日志」；导出后才会清标记。
            pendingCrashSummary()?.let {
                showSystemToast("上次启动崩溃过，可在设置 → 关于 导出日志")
            }
        }
        // 栈式导航：返回一定是回到上一层（同值路由回退而不是再压一层，见 ErosNavigator）
        val nav = rememberErosNavigator(Route.Tabs)
        val backStack = nav.backStack
        val push: (Route) -> Unit = nav::push
        val pop: () -> Unit = nav::pop
        // tab 下标放进程级：Activity 重建（回桌面再回来）也停在同一页
        val tabIndex = AppStateHolder.selectedTab

        // 页面状态统一放这里：进出页面不重建，一个启动周期内只加载一次。
        // 进程级持有：Activity 重建（回桌面再回来）不会丢列表数据
        val store = AppStateHolder.store
        val homeState = AppStateHolder.homeState
        val favState = AppStateHolder.favouritesState
        val searchState = AppStateHolder.searchState
        // 搜索是浮层模式而不是路由（方案 A，对齐官方 GlassSearchOverlay）：
        // 页面淡出的同时覆盖层从窗口顶部滑入，因此不需要压栈、也不影响返回栈。
        var searchExpanded by remember { mutableStateOf(false) }
        val openTag: (String) -> Unit = { q -> searchState.search(q); searchExpanded = true }
        val openUploader: (String) -> Unit = { u -> searchState.searchUploader(u); searchExpanded = true }
        val homeGridState = AppStateHolder.homeGridState
        val topListState = AppStateHolder.topListState
        val rankGridState = AppStateHolder.rankGridState
        // 不用 rememberLazyGridState（它会跨进程恢复旧偏移）；App 级 remember 足够保持会话内位置
        val favGridState = AppStateHolder.favGridState

        // 预测式返回已固定关闭：统一用系统级回调接管，系统不再索取跟手进度。
        // 这个回调注册在 OnBackInvokedDispatcher 的 PRIORITY_OVERLAY，优先级高于 Compose 的
        // BackHandler。注意 Android 的 OnBackInvokedCallback 是 void、没有「未处理」返回值，
        // 我们的适配层在返回 false 时会直接 finish()（见 BackInterceptor.android.kt），
        // 所以搜索展开时只能在这里消费返回键（浮层内部的 NavigationBackHandler 只作为
        // 桌面/无拦截器环境的后备，不能在 Android 上同时接管，否则两处会抢同一次返回）。
        val onSystemBack = rememberUpdatedState<() -> Boolean> {
            when {
                searchExpanded -> {
                    searchExpanded = false
                    true
                }
                backStack.size > 1 -> {
                    pop()
                    true
                }
                else -> false // 栈底交给 Activity 退出
            }
        }
        LaunchedEffect(Unit) {
            setPlainBackInterceptor(true) { onSystemBack.value() }
        }

        // 官方示例的转场效果：跟随设备屏幕圆角、轻微调暗、底部露出宿主背景
        // 侧滑返回已固定关闭
        val swipeDismiss = NavSwipeDirection.None
        // 从侧栏/底栏切根 tab 时，官方会抑制 NavDisplay 的推入/退出动画
        // （AppContent.kt: onBeforeNavigate = { suppressNavTransition = true }）：
        // 这里用一帧的短暂的标志实现同样效果，避免「详情→列表」时可见的整页滑入。
        var suppressNavTransition by remember { mutableStateOf(false) }
        LaunchedEffect(suppressNavTransition) {
            if (suppressNavTransition) {
                withFrameNanos { }
                suppressNavTransition = false
            }
        }
        val navTransition = if (suppressNavTransition) NavTransitions.None else NavTransitions.MiuixDefault
        val navEffects = NavDisplayEffects(
            cornerClipRadius = rememberNavSystemCornerRadius(),
            cornerClipMode = NavCornerClipMode.All,
            dimAmount = 0.28f,
            backdropColor = MiuixTheme.colorScheme.background,
        )

        // 统一底栏、记录层与两种 rail 的宿主都在 ErosAppChrome（Phase 1 纯搬迁）；
        // 这里只保留导航状态与页面入口。
        val currentRoute = nav.current
        val detailRoute = nav.detail
        val activeDetail: DetailState? = detailRoute?.let { r ->
            remember(r.gid, r.token) {
                store.get("detail:${r.gid}:${r.token}") { DetailState(r.gid, r.token) }
            }
        }
        // 收藏夹弹层打开时隐藏底栏，避免底栏盖在弹层上方
        val showAppBar = (currentRoute is Route.Tabs || detailRoute != null) &&
            !OverlayState.anyOpen

        // 宽屏（≥600dp）把导航搬到左侧 rail：除沉浸式阅读器外，所有路由都保留侧栏，
        // 这样进搜索/评论/账号/设置类页面时也能直接切 tab。
        val railAllowed = currentRoute !is Route.Reader
        val useNavigationRail = railAllowed && rememberUseNavigationRail()
        // 导航栏样式 = 玻璃时，宽屏侧栏也换成玻璃版：面板浮在页面上，页面按展开进度让位
        val useGlassRail = useNavigationRail && AppSettings.navBarStyle == 3
        val railMode = when {
            useGlassRail -> ErosChromeRail.Glass
            useNavigationRail -> ErosChromeRail.Native
            else -> ErosChromeRail.None
        }

        // 详情动作由底栏与两种 rail 共用（状态与回调只算一次，见 rememberDetailActions）
        val detailActions = rememberDetailActions(activeDetail) { st ->
            push(Route.Reader(st.gid, st.token, st.detail?.pages ?: 0))
        }
        // 从侧栏/底栏切根 tab：抑制 NavDisplay 转场并一路退回 Tabs 页
        // （官方 AppContent.kt 的 onBeforeNavigate = { suppressNavTransition = true }）。
        val onSelectTab: (Int) -> Unit = { index ->
            suppressNavTransition = true
            AppStateHolder.selectedTab = index
            nav.popToTabs()
        }

        // 底栏目的地：null 表示当前不显示。
        // rail 下的 Tabs 页由 rail 负责导航，不需要底栏；详情页仍保留动作栏
        // （对齐官方 GlassPage：rail 只负责导航，页面自带底栏）。
        val railVisible = railMode != ErosChromeRail.None
        val barDestination: BarDestination? = when {
            !showAppBar -> null
            detailRoute != null -> BarDestination.Detail(detailActions)
            !railVisible -> BarDestination.Tabs(tabIndex)
            else -> null
        }

        // H2 根浮层宿主：最外层一个透明 Scaffold，只为 OverlayDialog / OverlayBottomSheet /
        // popup 提供「根 Scaffold 状态」。页面 Scaffold 会复用它的 LocalRoot* 状态
        // （miuix Scaffold.kt:104-107），renderInRootScaffold=true 的浮层因此渲染在这一层，
        // 盖在侧栏与底栏之上（官方 AppContent.kt:308 同做法）。契约见 UI_LAYERS.md。
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
        ) { _ ->
            // 搜索浮层挂在 chrome 之外的根 Box 上（见下），页面整棵树按同一 alpha 淡出。
            val searchBarAlpha = rememberGlassSearchBarAlpha(searchExpanded)
            Box(Modifier.fillMaxSize()) {
                // 顶栏据此与浮层同步收放（官方 GlassPage 的 glassSearchBarTransition 语义）。
                CompositionLocalProvider(
                    LocalSearchModeExpanded provides searchExpanded,
                    LocalSearchBarAlpha provides searchBarAlpha,
                ) {
                    ErosAppChrome(
                rail = railMode,
                selectedTab = tabIndex,
                // 侧栏 / 底栏随页面一起淡出，搜索期间由浮层挡住点击（侧栏 A）。
                onSelectTab = { tab ->
                    searchExpanded = false
                    onSelectTab(tab)
                },
                detailActions = detailActions,
                destination = barDestination,
                content = {
                    NavDisplay(
                        modifier = Modifier.fillMaxSize(),
                        backStack = backStack,
                        onBack = pop,
                        transition = navTransition,
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
                    // 普通搜索入口每次都是干净的一页：不保留上次的关键词与结果。
                    onOpenSearch = {
                        searchState.reset()
                        searchExpanded = true
                    },
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
                    onSearchTag = openTag,
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
        },
                bottomBar = { backdrop ->
                    ErosBottomBarHost(
                        destination = barDestination,
                        // 与侧栏同一规则：搜索浮层打开时点底栏先退出搜索。
                        onSelectTab = { tab ->
                            searchExpanded = false
                            onSelectTab(tab)
                        },
                        backdrop = backdrop,
                    )
                },
                contentAlpha = searchBarAlpha.value,
            )
                }

                // 搜索是窗口级浮层（L3），挂在 chrome 之外的根 Box 上：盖住整个窗口
                // （含侧栏与底栏），页面整棵树按 searchBarAlpha 淡出 —— 与官方示例
                // GlassPageHost 的「overlay 与淡出的 Scaffold 同级」一致。
                ErosSearchOverlay(
                    expanded = searchExpanded,
                    state = searchState,
                    onDismissRequest = { searchExpanded = false },
                    onGalleryClick = { g ->
                        searchExpanded = false
                        push(Route.Detail(g.gid, g.token))
                    },
                    onOpenGallery = { ref ->
                        searchExpanded = false
                        push(Route.Detail(ref.gid, ref.token))
                    },
                )
                // 帧率浮层：official example 的 FPSMonitor
                if (AppSettings.showFps) {
                    FPSMonitor(
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(top = 4.dp),
                    )
                }

                // popup / dialog 宿主由上面这个根 Scaffold 的 popupHost 槽位提供（默认 MiuixPopupHost）。
            }
        }
    }
}
