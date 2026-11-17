// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.design

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private var provider: () -> Dp = { 0.dp }

    /** 由 `App.kt` 在组合期更新；只是替换引用，不读状态。 */
    fun update(p: () -> Dp) {
        provider = p
    }

    /** **只在 layout 阶段调用**：这时读到的是最新的 provider，而不是某一帧组合时捕获的旧 lambda。 */
    fun current(): Dp = provider()
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
    return layout { measurable, constraints ->
        // 在这里（layout 阶段）才取 provider：竖屏→横屏切换后页面不一定重组，
        // 但一定会重新测量，所以能拿到 App.kt 最新的让位宽度。
        val start = GlassRailStartInset.current().roundToPx().coerceIn(0, constraints.maxWidth)
        val placeable = measurable.measure(constraints.offset(horizontal = -start))
        layout(placeable.width + start, placeable.height) {
            placeable.placeRelative(start, 0)
        }
    }
}

/**
 * 底部浮层（主底栏、阅读器进度栏/动作栏）在宽屏下的收窄规则：
 * 窗口宽度超过 [ErosBottomBarWideBreakpoint] 时，胶囊固定收窄到 [ErosBottomBarMaxWidth]。
 *
 * 数值来自官方示例的宽屏处理（`example/.../AppContent.kt` 的底栏宽度约束）：
 * 平板/横屏下不让胶囊横跨整块屏幕，保持和手机一致的观感。
 */
val ErosBottomBarWideBreakpoint = 600.dp
val ErosBottomBarMaxWidth = 344.dp

/**
 * 给底部胶囊套上宽屏收窄：超过断点就按 [ErosBottomBarMaxWidth] 重新测量。
 *
 * 只改自身宽度，不做定位 —— 调用方用 `Box(contentAlignment = BottomCenter)` 或
 * `Column(horizontalAlignment = CenterHorizontally)` 负责居中（与 [app.eros.shared.ui.ErosAppBottomBar]
 * 在 `App.kt` 里的挂法一致）。
 */
fun Modifier.erosBottomBarWidthLimit(): Modifier = layout { measurable, constraints ->
    val bounded = constraints.hasBoundedWidth
    val wide = bounded && constraints.maxWidth > ErosBottomBarWideBreakpoint.roundToPx()
    // 不能 coerceIn(constraints.minWidth, …)：调用方常用 fillMaxWidth()，此时 min == max == 全宽，
    // 收窄值会被 min 顶回去（实测会打印 max=2500 min=2500 -> 2500）。这里直接改写约束。
    val width = if (wide) ErosBottomBarMaxWidth.roundToPx() else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/**
 * 玻璃侧栏对顶栏的影响（宽屏 chrome）：
 *  - [railMode]：当前是否走玻璃侧栏布局（宽屏）。为 true 时顶栏 `expandable = false`，
 *    也就是官方示例在宽屏下「没有大标题」的行为。
 *  - [expanded]：侧栏是否展开。侧栏收成顶部胶囊时会盖住标题，因此折叠态不显示标题
 *    （官方用 `titleAlpha` 驱动淡出；这里改用组合期状态切换，因为实测把进度传进
 *    `titleAlpha` 的 lambda 在 draw 阶段读不到更新）。
 */
object GlassRailChrome {
    var railMode by mutableStateOf(false)
    var expanded by mutableStateOf(false)
}
