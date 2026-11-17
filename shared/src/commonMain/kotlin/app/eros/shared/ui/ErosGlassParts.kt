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

/**
 * 分段 Tab。
 *
 * [backdrop] 非空且模糊可用时走 miuix 的 `GlassSegmentedTabRow`（一条轨道 + 滑动指示器，玻璃底）。
 *
 * **注意**：`backdrop` 必须是「不包含这个 Tab 自身」的那一层的 backdrop。本项目页面骨架把整页内容
 * 记在一层里（`Modifier.layerBackdrop(pageBackdrop)`），如果 Tab 本身就在这一层里、又拿这一层去采样，
 * 就会采到自己 —— hwui 的 `RenderNode::prepareTreeImpl` 会无限递归、直接栈溢出崩进程。
 * 所以只有把 Tab 放在内容层**外面**（例如浮在列表上方的兄弟节点）时才能传 backdrop；
 * 列表里随内容滚动的 Tab 请传 `null`，走普通 `TabRow`。
 */
@Composable
fun ErosTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: LayerBackdrop? = null,
    modifier: Modifier = Modifier,
) {
    val glassBackdrop = backdrop.takeIf { it != null && glassAvailable() }
    if (glassBackdrop != null) {
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
 * 注意：弹层自身不在页面内容层里（它挂在 Scaffold 浮层），所以传页面内容层的 `backdrop`
 * 不会自引用；但**调用方必须确认这一点** —— 如果调用点在页面内容层内部，请传 `null`
 * （弹层退回不透明底色），否则会重演 [ErosTabRow] 的 hwui 栈溢出。
 */
@Composable
fun ErosDropdownPreference(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: LayerBackdrop? = null,
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
            renderInRootScaffold = false,
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
 * 工具栏是浮在页面之上的兄弟节点、不在内容层里，所以采样 `LocalPageBackdrop` 不会自引用。
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
