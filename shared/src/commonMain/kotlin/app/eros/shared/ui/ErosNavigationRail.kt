// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import app.eros.shared.settings.AppSettings
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
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
