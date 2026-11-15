// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eros.shared.ui.liquid.IosLiquidGlassNavigationBar
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * iOS 风格底栏的一项。
 *
 * @param active 已收藏 / 下载中 / 已读这类「当前动作已生效」的状态，用主色着色。
 * @param enabled 详情页数据尚未就绪时置 false，点击被忽略。
 * @param badge 右上角数字徽章，null 表示不显示。
 */
internal data class IosBarItem(
    val icon: ImageVector,
    val label: String,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val badge: String? = null,
)

/**
 * 只量测一次子项宽度的容器：子项按无界约束测量后**不摆放**，
 * 自身占 0×0，既不绘制也不参与命中测试。
 *
 * 用于拿到「悬浮」样式同款条目的自然宽度，让详情态的三项胶囊与它同宽。
 */
@Composable
private fun MeasureWidthOnly(
    onWidth: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = Modifier) { measurables, _ ->
        val placeable = measurables.firstOrNull()?.measure(Constraints())
        onWidth(placeable?.width ?: 0)
        layout(0, 0) {}
    }
}

/**
 * 导航态每项的估算宽度：手机（375dp 宽）上 5 项胶囊约 327dp，
 * 用 64dp/项 + 8dp 误差 ≤1dp；宽屏据此定宽，不再被 fillMaxWidth 拉满整屏。
 */
private val IosTabWidthDp = 64.dp

/**
 * iOS 风格（液态玻璃）底栏适配层。
 *
 * 外观与交互全部交给 [IosLiquidGlassNavigationBar]（`ui/liquid` 下改编自
 * Kyant0/AndroidLiquidGlass 的实现）：胶囊主体用 `drawBackdrop` 做 vibrancy + blur + 镜头折射，
 * 隐藏镜像层把「胶囊内的图标文字」录进 `tabsBackdrop`，选中指示器再对合成后的背景做
 * `lens(...)` 折射 + 内阴影 + 边缘高光，拖动/按压/回弹走示例的 `DampedDragAnimation`。
 *
 * eros 只补三件事：
 * 1. 详情态（三个动作项）用 [measureContent] 量出「悬浮」样式的自然宽度，胶囊按此收窄；
 * 2. 导航态没有 [measureContent]，按项宽估算出定宽，横屏/大屏上不会铺满整屏；
 * 3. [activeIndices] 里的事项用主色着色，[switchable] = false 时禁用横向拖拽。
 */
@Composable
internal fun ErosIosBottomBar(
    items: List<IosBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    switchable: Boolean = true,
    repeatActivation: Boolean = false,
    backdrop: LayerBackdrop? = null,
    modifier: Modifier = Modifier,
    measureContent: (@Composable () -> Unit)? = null,
) {
    var measuredWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxWidth()) {
        if (measureContent != null && items.isNotEmpty()) {
            MeasureWidthOnly(onWidth = { measuredWidthPx = it }) { measureContent() }
        }

        // 胶囊宽度：详情态用量出的条目自然宽（左右各 +12dp 内边距），
        // 导航态按项宽估算，手机仍与原设计同宽、宽屏不再铺满。
        val barWidth: Dp? = when {
            measureContent != null && measuredWidthPx > 0 ->
                with(density) { (measuredWidthPx + 24.dp.roundToPx()).toDp() }

            measureContent == null && items.isNotEmpty() -> IosTabWidthDp * items.size + 8.dp
            else -> null
        }

        IosLiquidGlassNavigationBar(
            items = items.map { NavigationItem(it.label, it.icon) },
            selectedIndex = selectedIndex,
            onItemClick = { index ->
                if (items.getOrNull(index)?.enabled != false) onSelect(index)
            },
            backdrop = backdrop,
            isBlurActive = backdrop != null,
            badge = { index ->
                val text = items.getOrNull(index)?.badge
                if (text == null) {
                    null
                } else {
                    val content: @Composable () -> Unit = { Badge { Text(text) } }
                    content
                }
            },
            barWidth = barWidth,
            activeIndices = items.mapIndexedNotNull { index, item -> if (item.active) index else null }.toSet(),
            switchable = switchable,
            repeatActivation = repeatActivation,
        )
    }
}
