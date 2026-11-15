// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 每列的目标宽度。
 *
 * 取 175dp 而不是 190dp，是为了让「侧边导航栏常驻」的宽屏仍按内容宽落满：
 * 2560×1440 @360dpi ≈ 1138dp，减去 rail 80dp 与左右内边距后约 1034dp，
 * 1034/175 ≈ 5.9 → 6 列；若用 190dp 会掉到 5 列，比改动前更稀。
 */
private const val GalleryColumnWidthDp = 175f

/** 网格左右各 12dp 的 contentPadding，算可用宽度时要扣掉。 */
val GalleryGridSidePadding: Dp = 12.dp

/**
 * 画廊网格列数：每列约 175dp，四舍五入取整，2–6 列。
 *
 * 用四舍五入而不是向下取整，是为了让宽屏正好铺满：1138dp（含 rail）→ 6 列；
 * 折叠屏展开 665–854dp → 4 列；手机竖屏 375dp → 2 列。上限 6 列，卡片不会被拉成巨幅。
 *
 * @param usableWidth 网格实际可用的宽度（已扣掉左右内边距、rail 等）。
 *   不传则退回窗口宽度——rail 展开时窗口宽 ≠ 内容宽，会多算一列，所以列表页都应显式传。
 */
@Composable
fun rememberGalleryColumns(usableWidth: Dp? = null): Int {
    val density = LocalDensity.current
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val width = usableWidth ?: with(density) { windowWidth.toDp() }
    return remember(width) {
        (width.value / GalleryColumnWidthDp).roundToInt().coerceIn(2, 6)
    }
}

/**
 * 详情页预览网格列数：比列表多一列，最多 7 列。
 *
 * 预览格子里是整页图，175dp 一列偏大；+1 列让手机保持 3 列（和原来的固定值一致），
 * 平板/横屏自动 5–7 列，避免整页图被拉成巨幅。
 */
@Composable
fun rememberPreviewColumns(usableWidth: Dp? = null): Int {
    return (rememberGalleryColumns(usableWidth) + 1).coerceAtMost(7)
}
