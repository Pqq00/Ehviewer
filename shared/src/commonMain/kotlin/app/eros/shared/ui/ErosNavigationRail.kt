// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.eros.shared.settings.AppSettings
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassNavigationRail
import top.yukonga.miuix.kmp.glass.GlassNavigationRailDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 侧边导航栏的启用断点：窗口宽度达到该值就用 rail 代替底部导航栏。 */
val NavigationRailBreakpoint = 600.dp

/**
 * 窗口宽度是否已经达到侧边导航栏的启用断点（不看开关本身）。
 *
 * 手机（<600dp）永远是 false，所以「侧边导航栏」这个设置项在手机上也不必出现。
 */
@Composable
fun rememberNavigationRailBreakpointReached(): Boolean {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    return with(density) { widthPx.toDp() } >= NavigationRailBreakpoint
}

/** 玻璃侧栏默认展开的宽度断点：平板与横屏大屏直接展开成面板，窄一点的横屏先收成胶囊。 */
val GlassNavigationRailExpandedBreakpoint = 840.dp

/** 玻璃侧栏首次出现时是否直接展开（宽 ≥ [GlassNavigationRailExpandedBreakpoint]）。 */
@Composable
fun rememberGlassNavigationRailStartsExpanded(): Boolean {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    return with(density) { widthPx.toDp() } >= GlassNavigationRailExpandedBreakpoint
}

/**
 * 是否用左侧 [NavigationRail] 代替底部导航栏。
 *
 * 开关打开且窗口宽度达到 [NavigationRailBreakpoint] 时命中；手机（<600dp）永远不会命中。
 */
@Composable
fun rememberUseNavigationRail(): Boolean {
    if (!AppSettings.navRail) return false
    return rememberNavigationRailBreakpointReached()
}

/**
 * 宽屏侧边导航栏。
 *
 * 结构与底栏保持一致：Tabs 页只显示五个主导航项；详情页把主项置灰，并在顶部（主导航项
 * 之前）插入收藏 / 下载 / 阅读三个动作（每个动作独立控制 enabled，下载项直接显示进度文案）。
 * 顶部由 miuix 自带的展开/收起按钮控制 80dp ↔ 240dp（展开后显示文字）。
 */
@Composable
fun ErosNavigationRail(
    isDetail: Boolean,
    selectedTab: Int,
    detailEnabled: Boolean,
    detailFavoriteCount: Int,
    detailReadPage: Int?,
    detailDownloadLabel: String,
    detailDownloadActive: Boolean,
    onSelectTab: (Int) -> Unit,
    onDetailFavorite: () -> Unit,
    onDetailDownload: () -> Unit,
    onDetailRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val railState = rememberNavigationRailState()
    NavigationRail(
        state = railState,
        modifier = modifier,
        color = MiuixTheme.colorScheme.surface,
    ) {
        // 详情页的三个动作项排在主导航项上方，和底栏「导航项 → 详情操作」的顺序区分开。
        if (isDetail) {
            NavigationRailItem(
                selected = false,
                onClick = onDetailFavorite,
                icon = MiuixIcons.Favorites,
                label = if (detailFavoriteCount > 0) "收藏 $detailFavoriteCount" else "收藏",
                enabled = detailEnabled,
            )
            NavigationRailItem(
                selected = false,
                onClick = onDetailDownload,
                icon = MiuixIcons.Download,
                label = detailDownloadLabel,
                enabled = detailEnabled && !detailDownloadActive,
            )
            NavigationRailItem(
                selected = false,
                onClick = onDetailRead,
                icon = MiuixIcons.Play,
                label = "阅读",
                enabled = detailEnabled,
                badge = detailReadPage?.takeIf { it > 0 }?.let { page ->
                    { Badge { Text(page.toString()) } }
                },
            )
            Spacer(Modifier.height(12.dp))
        }
        AppTabs.forEachIndexed { index, tab ->
            NavigationRailItem(
                selected = !isDetail && selectedTab == index,
                onClick = { onSelectTab(index) },
                icon = tab.icon,
                label = tab.title,
                enabled = !isDetail,
            )
        }
    }
}

/**
 * 玻璃侧栏（平板 / 横屏，仅在「导航栏样式 = 玻璃」时启用）。
 *
 * 与 [ErosNavigationRail] 的三点区别：
 * - 面板**浮在页面之上**，不占 Row 的一列；页面靠 [glassRailContentInset] 让位；
 * - 展开 / 收起（44dp 胶囊 ↔ 250dp 面板）由组件内置的开关胶囊负责，`state` 由调用方持有；
 * - [GlassNavigationItem] 没有 `enabled`，禁用语义改成在回调里判空（未加载完的详情、已在下载中的项点上去是空操作）。
 *
 * 详情页把三项动作排在最前，选中态仍落在「当前 tab」上（选中项 = tab 序号 + 动作数）。
 */
@Composable
fun ErosGlassNavigationRail(
    isDetail: Boolean,
    selectedTab: Int,
    detailEnabled: Boolean,
    detailFavoriteCount: Int,
    detailReadPage: Int?,
    detailDownloadLabel: String,
    detailDownloadActive: Boolean,
    onSelectTab: (Int) -> Unit,
    onDetailFavorite: () -> Unit,
    onDetailDownload: () -> Unit,
    onDetailRead: () -> Unit,
    state: GlassNavigationRailState,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    val actionCount = if (isDetail) 3 else 0
    val items = remember(
        isDetail,
        detailFavoriteCount,
        detailReadPage,
        detailDownloadLabel,
    ) {
        buildList {
            if (isDetail) {
                add(
                    GlassNavigationItem(
                        icon = MiuixIcons.Favorites,
                        label = if (detailFavoriteCount > 0) "收藏 $detailFavoriteCount" else "收藏",
                        contentDescription = "收藏",
                    ),
                )
                add(
                    GlassNavigationItem(
                        icon = MiuixIcons.Download,
                        label = detailDownloadLabel,
                        contentDescription = "下载",
                    ),
                )
                add(
                    GlassNavigationItem(
                        icon = MiuixIcons.Play,
                        label = "阅读",
                        contentDescription = "阅读",
                        badge = detailReadPage?.takeIf { it > 0 }?.let { page ->
                            { Badge { Text(page.toString()) } }
                        },
                    ),
                )
            }
            AppTabs.forEach { tab ->
                add(
                    GlassNavigationItem(
                        icon = tab.icon,
                        label = tab.title,
                        contentDescription = tab.title,
                    ),
                )
            }
        }
    }
    GlassNavigationRail(
        items = items,
        selectedIndex = actionCount + selectedTab,
        onSelect = { index ->
            if (isDetail && index < actionCount) {
                when (index) {
                    0 -> if (detailEnabled) onDetailFavorite()
                    1 -> if (detailEnabled && !detailDownloadActive) onDetailDownload()
                    else -> if (detailEnabled) onDetailRead()
                }
            } else {
                onSelectTab(index - actionCount)
            }
        },
        backdrop = backdrop,
        modifier = modifier,
        state = state,
    )
}

/**
 * 让页面给玻璃侧栏腾出位置：按 [GlassNavigationRailState.progress]（面板展开程度 0→1）在 layout 阶段
 * 收窄并右移内容，不触发页面重组。
 */
@Composable
fun Modifier.glassRailContentInset(state: GlassNavigationRailState): Modifier {
    val density = LocalDensity.current
    return this.layout { measurable, constraints ->
        val shift = with(density) {
            ((GlassNavigationRailDefaults.Margin + GlassNavigationRailDefaults.ExpandedWidth) * state.progress)
                .roundToPx()
        }
        val maxWidth = (constraints.maxWidth - shift).coerceAtLeast(0)
        val placeable = measurable.measure(
            Constraints(
                minWidth = 0,
                maxWidth = maxWidth,
                minHeight = constraints.minHeight,
                maxHeight = constraints.maxHeight,
            ),
        )
        layout(constraints.maxWidth, placeable.height) {
            placeable.placeRelative(shift, 0)
        }
    }
}
