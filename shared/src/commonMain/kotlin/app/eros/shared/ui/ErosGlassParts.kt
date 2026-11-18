// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import app.eros.shared.settings.AppSettings
import app.eros.shared.ui.effect.isShaderSupported
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.glass.GlassFloatingToolbar
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.GlassTabRow
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

/**
 * 当前页面的「内容层 backdrop」，由 [ErosPageScaffold] 下发。
 *
 * 页内玻璃控件（Tab、弹层）拿它做采样源；没有下发时退回普通控件。
 * 注意：它必须是不包含使用者自身的那一层，否则 hwui 会因自引用递归而栈溢出（见 [ErosTabRow]）。
 */
val LocalPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }


/** 模糊是否可用：栏模糊开着、且设备支持 RuntimeShader。 */
private fun glassAvailable(): Boolean = AppSettings.barBlurEnabled && isShaderSupported()

/** 玻璃分段 Tab 的两种形态。 */
enum class ErosTabVariant {
    /** 一条轨道 + 滑动指示器（少数互斥选项，如「评论 / 预览」）。 */
    Segmented,

    /** 每颗 tab 各自一个胶囊，中间没有滑块（平级的多个筛选项）。 */
    Pill,
}

/**
 * 分段 Tab。
 *
 * [backdrop] 非空且模糊可用时走 miuix 的 `GlassSegmentedTabRow`（一条轨道 + 滑动指示器，玻璃底）。
 *
 * **注意**：`backdrop` 必须是「不包含这个 Tab 自身」的那一层的 backdrop，否则会自采样
 * （hwui prepare 递归栈溢出）。判断方法与守卫见 `ErosGlassRecordLayer`：
 * 只有把 Tab 放在记录层外面时才能传 backdrop；列表里随内容滚动的 Tab 请传 `null`。
 */
@Composable
fun ErosTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: LayerBackdrop? = null,
    modifier: Modifier = Modifier,
    variant: ErosTabVariant = ErosTabVariant.Segmented,
) {
    val glassBackdrop = backdrop.takeIf { it != null && glassAvailable() }
    if (glassBackdrop != null && variant == ErosTabVariant.Pill) {
        GlassTabRow(
            tabs = tabs,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            backdrop = glassBackdrop,
            modifier = modifier,
        )
    } else if (glassBackdrop != null) {
        GlassSegmentedTabRow(
            tabs = tabs,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            backdrop = glassBackdrop,
            modifier = modifier,
        )
    } else {
        TabRow(
            tabs = tabs,
            selectedTabIndex = selectedIndex,
            onTabSelected = onSelect,
            modifier = modifier,
        )
    }
}

/**
 * 下拉选项行：玻璃可用时走 `GlassDropdownPreference`（弹层是玻璃面板），否则退回
 * `OverlayDropdownPreference`。
 *
 * `renderInRootScaffold = true`：弹层被挂到根 Scaffold 的浮层里，因此**不在**页面内容层内，
 * 采样页面内容层的 `backdrop` 不会自引用（这是 [ErosTabRow] 那个 hwui 栈溢出的前提条件）。
 * 默认 `backdrop = LocalPageBackdrop.current`，即页面骨架下发的这一层；没有骨架（自带 Scaffold
 * 的页面，例如阅读器）的调用点请显式传自己的 backdrop 或 `null`。
 */
@Composable
fun ErosDropdownPreference(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: LayerBackdrop? = LocalPageBackdrop.current,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: String? = null,
) {
    if (glassAvailable()) {
        GlassDropdownPreference(
            title = title,
            items = items,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            backdrop = backdrop,
            modifier = modifier,
            enabled = enabled,
            summary = summary,
            renderInRootScaffold = true,
        )
    } else {
        OverlayDropdownPreference(
            title = title,
            items = items,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            modifier = modifier,
            enabled = enabled,
            summary = summary,
        )
    }
}

/**
 * 多选态底部浮动工具栏：玻璃可用时走 `GlassFloatingToolbar`（采样页面内容层 backdrop），
 * 否则退回普通 `FloatingToolbar`。
 *
 * 工具栏是玻璃栏：调用点要用 `ErosGlassBar` 包起来并放在记录层之外（见 `ErosGlassRecordLayer`）。
 */
@Composable
fun ErosFloatingToolbar(
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = LocalPageBackdrop.current,
    content: @Composable () -> Unit,
) {
    val glassBackdrop = backdrop.takeIf { it != null && glassAvailable() }
    if (glassBackdrop != null) {
        GlassFloatingToolbar(
            modifier = modifier,
            backdrop = glassBackdrop,
            content = content,
        )
    } else {
        FloatingToolbar(modifier = modifier, content = content)
    }
}
