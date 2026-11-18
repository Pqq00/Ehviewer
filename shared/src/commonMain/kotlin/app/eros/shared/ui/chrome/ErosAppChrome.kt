package app.eros.shared.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.eros.shared.ui.ErosGlassNavigationRail
import app.eros.shared.ui.ErosNavigationRail
import app.eros.shared.ui.LocalTopAppBarTitleAlpha
import app.eros.shared.ui.design.GlassRailChrome
import app.eros.shared.ui.design.GlassRailStartInset
import app.eros.shared.ui.rememberBarBackdrop
import app.eros.shared.ui.rememberGlassNavigationRailStartsExpanded
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationRailDefaults
import top.yukonga.miuix.kmp.glass.rememberGlassNavigationRailState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 宽屏时导航所在的位置。 */
internal enum class ErosChromeRail { None, Native, Glass }

/**
 * 窗口级 chrome：记录层、左侧 rail、底部动作栏的宿主（Phase 1 从 `App()` 抽出）。
 *
 * 三份职责在这里收口，`App()` 只保留导航、页面状态与 NavDisplay：
 * - **记录层**：[ErosGlassRecordLayer] 记录整窗，页面让位由内层内容自己负责；
 * - **rail**：native 占 Row 一列，glass 浮在页面之上（采样 `pageBackdrop`）；
 * - **底栏**：用 [ErosGlassBar] 挂在记录层之外，以内容列居中，玻璃侧栏展开时
 *   按 [erosGlassRailStartInset] 右移。
 *
 * 这是 `UI_LAYERS.md` 里的 L0 窗口 chrome：只允许采样 L1 页面记录层，不得放进页面记录层内；
 * L3 浮层要盖住 rail / 底栏，渲染宿主由 `ErosOverlayHost`（H2）负责，不再依赖页面 Scaffold。
 *
 * @param content 页面内容（`NavDisplay`）；chrome 已提供标题透明度 CompositionLocal。
 * @param bottomBar 底栏内容，传入记录层（供玻璃底栏模糊采样）。
 */
@Composable
internal fun ErosAppChrome(
    rail: ErosChromeRail,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    detailActions: DetailActions,
    destination: BarDestination?,
    content: @Composable () -> Unit,
    bottomBar: @Composable (LayerBackdrop?) -> Unit,
) {
    // 底栏留白要提供给「页面内容」与「底栏」的共同上层：页面在自己的 LazyColumn
    // contentPadding 里读 LocalAppBottomBarInset，如果只包 bottomBar 就读不到。
    val barInset = rememberAppBottomBarInset(destination, rail != ErosChromeRail.None)
    CompositionLocalProvider(LocalAppBottomBarInset provides barInset) {
        ErosAppChromeContent(rail, selectedTab, onSelectTab, detailActions, content, bottomBar)
    }
}

@Composable
private fun ErosAppChromeContent(
    rail: ErosChromeRail,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    detailActions: DetailActions,
    content: @Composable () -> Unit,
    bottomBar: @Composable (LayerBackdrop?) -> Unit,
) {
    // 记录「页面内容」的层：侧栏/顶栏/底栏都采样它（官方把同一角色叫 sidebarBackdrop）。
    val pageBackdrop = rememberBarBackdrop()
    val glassRailState = rememberGlassNavigationRailState(
        initialExpanded = rememberGlassNavigationRailStartsExpanded(),
    )

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (rail == ErosChromeRail.Native) {
                ErosNavigationRail(
                    // rail 只做导航：详情动作交给底部动作栏（官方 Glass 页同一分工）。
                    isDetail = false,
                    selectedTab = selectedTab,
                    detailEnabled = detailActions.enabled,
                    detailFavoriteCount = detailActions.favoriteCount,
                    detailReadPage = detailActions.readPage,
                    detailDownloadLabel = detailActions.downloadLabel,
                    detailDownloadActive = detailActions.downloadActive,
                    onSelectTab = onSelectTab,
                    onDetailFavorite = detailActions.onFavorite,
                    onDetailDownload = detailActions.onDownload,
                    onDetailRead = detailActions.onRead,
                    // 场景切换时 NavDisplay 会画上一页的底图，rail 必须画在它之上
                    modifier = Modifier.zIndex(1f),
                )
            }

            Box(Modifier.weight(1f)) {
                // 对齐官方 AppContent.kt:335-376：整窗先铺底色并裁剪（clipToBounds），
                // 记录层（layerBackdrop）尺寸始终等于窗口；页面让位只作用于内层容器。
                // 记录层：页面内容记录成一层，侧栏/底栏采样它（规则见 ErosGlassRecordLayer）。
                ErosGlassRecordLayer(
                    backdrop = pageBackdrop,
                    layerName = "page (whole window)",
                    modifier = Modifier
                        .fillMaxSize()
                        // 整窗底色：玻璃侧栏展开时页面右移，左条不再被页面绘制，靠它兜住背景
                        .background(MiuixTheme.colorScheme.surface)
                        .clipToBounds(),
                ) {
                    // 玻璃侧栏让位：页面内容自己内缩（各页 ErosPageScaffold / 顶栏前景），
                    // 记录层保持整窗、内容坐标 = 窗口坐标 —— 侧栏采样到的就是真实页面。
                    GlassRailChrome.railMode = rail != ErosChromeRail.None
                    GlassRailChrome.expanded = glassRailState.expanded
                    // 对齐官方 example：让位量就是 (Margin + ExpandedWidth) * progress。
                    // provider 用 remember 固定实例：只有在「是否玻璃侧栏」或 state 对象变化时才换新，
                    // 换实例会让已测量过的页面重新测量（见 GlassRailStartInset 的注释）。
                    val railInsetProvider = remember(rail, glassRailState) {
                        {
                            if (rail == ErosChromeRail.Glass) {
                                (GlassNavigationRailDefaults.Margin + GlassNavigationRailDefaults.ExpandedWidth) *
                                    glassRailState.progress
                            } else {
                                0.dp
                            }
                        }
                    }
                    SideEffect { GlassRailStartInset.update(railInsetProvider) }

                    Box(Modifier.fillMaxSize()) {
                        // 官方 `LocalTopAppBarTitleAlpha provides { sidebarState.progress }`：
                        // 宽屏玻璃侧栏展开时标题淡入，收起时淡出（draw 阶段读 progress，不重组）。
                        // LocalChromeBackdrop：窗口级浮层（要盖住侧栏/底栏）的采样源 = 整页层。
                        CompositionLocalProvider(
                            LocalTopAppBarTitleAlpha provides {
                                if (rail == ErosChromeRail.Glass) glassRailState.progress else 1f
                            },
                            LocalChromeBackdrop provides pageBackdrop,
                        ) {
                            content()
                        }
                    }
                }

                // 统一底栏：Tabs 与 Detail 共用同一实例，只切换内容；用 ErosGlassBar 保证
                // 挂在记录层之外（规则见 ErosGlassRecordLayer）。玻璃侧栏展开时，底栏宿主
                // 内部再按 erosGlassRailStartInset 右移，与页面内容同一让位量。
                // 可见性、内容形态与底部留白都在 ErosBottomBarHost 里决定。
                Box(Modifier.align(Alignment.BottomCenter)) {
                    ErosGlassBar(name = "app bottom bar", backdrop = pageBackdrop) {
                        bottomBar(pageBackdrop)
                    }
                }
            }
        }

        // 玻璃侧栏：浮在页面之上，不占 Row 的一列（页面已按 state.progress 让位）
        if (rail == ErosChromeRail.Glass) {
            ErosGlassBar(name = "glass navigation rail", backdrop = pageBackdrop) {
                ErosGlassNavigationRail(
                    // rail 只做导航：详情动作交给底部动作栏（官方 Glass 页同一分工）。
                    isDetail = false,
                    selectedTab = selectedTab,
                    detailEnabled = detailActions.enabled,
                    detailFavoriteCount = detailActions.favoriteCount,
                    detailReadPage = detailActions.readPage,
                    detailDownloadLabel = detailActions.downloadLabel,
                    detailDownloadActive = detailActions.downloadActive,
                    onSelectTab = onSelectTab,
                    onDetailFavorite = detailActions.onFavorite,
                    onDetailDownload = detailActions.onDownload,
                    onDetailRead = detailActions.onRead,
                    state = glassRailState,
                    // 采样页面内容层；侧栏自己在记录层之外，不会采到自己。
                    backdrop = pageBackdrop,
                    modifier = Modifier.zIndex(2f),
                )
            }
        }
    }
}
