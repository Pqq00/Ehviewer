// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eros.shared.settings.AppSettings
import app.eros.shared.ui.ErosAppBottomBar
import app.eros.shared.ui.design.erosGlassRailStartInset
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 底栏当前要展示的形态。
 *
 * 底栏是窗口级单实例（Tabs ↔ Detail 的形态过渡是产品特性，官方示例没有），
 * 页面不直接决定底栏，只通过这个目的地声明「我现在是什么」。
 */
internal sealed interface BarDestination {
    /** Tabs 页：展示 5 个导航项。 */
    data class Tabs(val tabIndex: Int) : BarDestination

    /** 详情页：展示收藏 / 下载 / 阅读三项操作。 */
    data class Detail(val actions: DetailActions) : BarDestination
}

/**
 * 底栏占用的底部高度。页面内容据此给最后一行留白，避免被底栏盖住。
 *
 * 由 [rememberAppBottomBarInset] 统一计算、`ErosAppChrome` 下发；页面一律读这个值，
 * 不再各自接收 `bottomInset` 参数（以前 7 个页面各有一份参数，公式改动要逐个改）。
 */
internal val LocalAppBottomBarInset = compositionLocalOf<Dp> { 0.dp }

/**
 * 底栏占用的底部高度，这是「留白公式」唯一出现的地方。
 *
 * @param destination null 表示当前不该显示底栏（overlay 打开，或 rail 下的 Tabs 页）。
 * @param railVisible 宽屏 rail 是否在显示；rail 下的 Tabs 页由 rail 负责导航，没有底栏。
 */
@Composable
internal fun rememberAppBottomBarInset(destination: BarDestination?, railVisible: Boolean): Dp {
    val glassBar = AppSettings.navBarStyle == 3
    val safeBottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    return when {
        // rail 下的 Tabs 页没有底栏，内容不需要让位；详情页仍有动作栏，照常留白。
        railVisible && destination !is BarDestination.Detail -> 0.dp
        // 玻璃底栏的高度 54dp + 最少 24dp 间隙，其余样式沿用 96dp。
        glassBar -> maxOf(24.dp, safeBottomInset + 8.dp) + 54.dp + 8.dp
        else -> 96.dp
    }
}

/**
 * 底栏宿主：只负责按 [destination] 决定内容与可见性，留白由 [rememberAppBottomBarInset] 负责。
 *
 * 摆放位置由 `ErosAppChrome` 负责（记录层之外的 BottomCenter），
 * 玻璃侧栏展开时宿主内部按 `erosGlassRailStartInset` 随内容右移。
 *
 * @param backdrop 页面记录层，供玻璃底栏模糊采样（宿主不在记录层内，不会自采样）。
 */
@Composable
internal fun ErosBottomBarHost(
    destination: BarDestination?,
    onSelectTab: (Int) -> Unit,
    backdrop: LayerBackdrop?,
) {
    // 退出动画期间 destination 已是 null；保留最后一次非空值，避免底栏淡出时闪成 Tabs 形态。
    var shown by remember { mutableStateOf(destination) }
    if (destination != null) shown = destination

    // 顶层 AnimatedVisibility（非 Row/BoxScope 版本）：宿主内部没有额外接收者。
    androidx.compose.animation.AnimatedVisibility(
        visible = destination != null,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) +
            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(180)) { it / 2 },
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)) +
            androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(140)) { it / 2 },
    ) {
        val actions = (shown as? BarDestination.Detail)?.actions
        Box(Modifier.fillMaxWidth().erosGlassRailStartInset()) {
            ErosAppBottomBar(
                isDetail = actions != null,
                selectedTab = (shown as? BarDestination.Tabs)?.tabIndex ?: 0,
                onSelectTab = onSelectTab,
                detailEnabled = actions?.enabled ?: false,
                detailFavorited = actions?.favorited ?: false,
                detailFavoriteCount = actions?.favoriteCount ?: 0,
                detailReadPage = actions?.readPage,
                detailReadStarted = actions?.readStarted ?: false,
                detailDownloadLabel = actions?.downloadLabel ?: "下载",
                detailDownloadActive = actions?.downloadActive ?: false,
                onDetailFavorite = actions?.onFavorite ?: {},
                onDetailDownload = actions?.onDownload ?: {},
                onDetailRead = actions?.onRead ?: {},
                backdrop = backdrop,
            )
        }
    }
}
