// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * 骨架项数上限：超大屏 + 多列时不至于堆出成百上千个占位块。
 *
 * 这个上限只是防病态，正常平板/横屏离它很远（4K 横屏也只有十几项）。
 */
internal const val MaxSkeletonItems = 60

/** 当前窗口高度（dp）：给那些拿不到「剩余高度」的页面用（评论页、详情页追加卡）。 */
@Composable
internal fun rememberWindowHeightDp(): Dp {
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    return remember(windowHeight, density) { with(density) { windowHeight.toDp() } }
}

/**
 * 骨架卡单项高度估算（不含 item 间距）。
 *
 * 默认样式与 [SkeletonCard] 同构：封面按 4:3（`aspectRatio(0.75f)`）铺满卡宽，
 * 下方两条 12dp 文本条 + 内边距合计约 56dp；下载样式与真实下载卡同为 176dp。
 */
internal fun estimateSkeletonCardHeight(itemWidth: Dp, downloadStyle: Boolean): Dp =
    if (downloadStyle) CARD_HEIGHT + 16.dp else itemWidth / 0.75f + 56.dp

/**
 * 网格骨架要铺几项：按可用高度算行数，再多给一行兜住估算误差。
 *
 * 宁多勿少 —— 骨架网格 `userScrollEnabled = false`，超出视口的部分直接被裁掉，多画不会露馅；
 * 少画才是真正的大屏穿帮（下面留一片白）。
 */
internal fun skeletonGridItemCount(
    availableWidth: Dp,
    availableHeight: Dp,
    columns: Int,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 12.dp,
    downloadStyle: Boolean = false,
    headerHeight: Dp = 0.dp,
): Int {
    if (columns <= 0) return 0
    val usableWidth = (availableWidth - horizontalSpacing * (columns - 1)).coerceAtLeast(1.dp)
    val itemHeight = estimateSkeletonCardHeight(usableWidth / columns, downloadStyle)
    val usableHeight = (availableHeight - headerHeight).coerceAtLeast(0.dp)
    val rows = skeletonRowCount(usableHeight, itemHeight, verticalSpacing)
    return (rows * columns).coerceIn(columns, MaxSkeletonItems)
}

/** 纵向列表骨架要铺几行（评论页、历史页这类一条条铺满的页面）。 */
internal fun skeletonRowCount(
    availableHeight: Dp,
    rowHeight: Dp,
    rowSpacing: Dp = 8.dp,
): Int {
    val step = (rowHeight + rowSpacing).coerceAtLeast(1.dp)
    val rows = ceil((availableHeight + rowSpacing) / step).toInt().coerceAtLeast(1)
    return (rows + 1).coerceIn(1, MaxSkeletonItems)
}

/**
 * 详情页骨架里「剩余高度还能塞几张评论卡」。
 *
 * 详情页加载态在一个 `verticalScroll` 里，高度是内容高度、不会自动拉伸，
 * 所以只能反算：减去（顶栏留白 + 信息卡 + 标签卡 + 首张评论卡）的估算高度，剩下的按评论卡步进补齐。
 */
internal fun detailSkeletonExtraCards(
    windowHeight: Dp,
    hasInfoCard: Boolean,
    tagRows: Int,
    chromeHeight: Dp = 150.dp,
): Int {
    val infoHeight = if (hasInfoCard) 197.dp else 0.dp
    val tagHeight = 36.dp * tagRows
    val firstComment = 132.dp
    val consumed = chromeHeight + infoHeight + tagHeight + firstComment
    val missing = windowHeight - consumed
    if (missing <= 0.dp) return 0
    return (missing / 150.dp).toInt().coerceIn(0, 8)
}
