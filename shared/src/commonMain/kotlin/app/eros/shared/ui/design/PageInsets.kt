// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.design

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/**
 * 只消费水平方向的安全区（横屏刘海、折叠屏铰链、曲面屏侧边）。
 *
 * 对应官方 example 的 `Modifier.appPageHorizontalInsets()`（`utils/PageUtils.kt:123-127`）：
 * 页面内容与底部胶囊要避开侧边的摄像头/铰链，但不动垂直方向 —— 垂直留白由顶栏、底栏
 * 各自的安全区计算负责（[app.eros.shared.ui.ErosTopBar] / [app.eros.shared.ui.ErosBottomNavBar]）。
 *
 * 普通手机上水平安全区为 0，本修饰符没有任何效果。
 */
@Composable
fun Modifier.erosPageHorizontalInsets(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))

/**
 * 玻璃侧栏让位宽度的提供者。
 *
 * 由 `App.kt` 在组合期赋值为「读 `GlassNavigationRailState.progress` 的 lambda」，
 * 只在 layout 阶段被调用 —— 动画期间不会触发重组，只会重新测量。
 *
 * 用可变持有者而不是 `CompositionLocal`，是为了避免在 `App.kt` 里额外插一层
 * `CompositionLocalProvider` 打乱 `NavDisplay` 的 entry 作用域（那里手改花括号很容易出错）。
 *
 * 对应官方 example 的 `AppPageInsets.contentInsets`：`overlayStart = (Margin + ExpandedWidth) * progress`。
 */
object GlassRailStartInset {
    /** 返回当前应让开的起始边宽度；返回 0 表示不让位。 */
    var provider: () -> Dp = { 0.dp }
}

/**
 * 让页面内容避开展开的玻璃侧栏：**内容内缩，背景不动**。
 *
 * 这正是官方 `Modifier.appPageHorizontalInsets()` 的语义（KDoc 原文：
 * *Apply and consume horizontal page space without moving its backdrop or vertical content*）：
 * 记录层（`layerBackdrop`）始终是整窗、内容坐标与窗口坐标一致，所以侧栏采样到的是真实页面，
 * 不会出现「面板边上卷进一条卡片」「详情页共享元素残影」这类位移副作用。
 *
 * 注意与旧实现（`glassRailContentInset`，把整个页面容器 `placeRelative` 右移）的区别：
 * 位移会让记录层里出现空白硬边界，模糊核会把边界另一侧的卡片卷进面板。
 */
@Composable
fun Modifier.erosGlassRailStartInset(): Modifier {
    val startInset = GlassRailStartInset.provider
    return layout { measurable, constraints ->
        val start = startInset().roundToPx().coerceIn(0, constraints.maxWidth)
        val placeable = measurable.measure(constraints.offset(horizontal = -start))
        layout(placeable.width + start, placeable.height) {
            placeable.placeRelative(start, 0)
        }
    }
}
