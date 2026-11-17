// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.eros.shared.settings.AppSettings
import app.eros.shared.ui.effect.isShaderSupported
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

/** 模糊是否可用：栏模糊开着、且设备支持 RuntimeShader。 */
private fun glassAvailable(): Boolean = AppSettings.barBlurEnabled && isShaderSupported()

/**
 * 分段 Tab。**目前固定走普通 [TabRow]**。
 *
 * 原因：本项目的页面背景层是「整个内容 Box 挂 `Modifier.layerBackdrop(pageBackdrop)`」，
 * 页面内的元素如果又去采样同一个 backdrop，就会采到自己 —— miuix 的 `GlassSegmentedTabRow`
 * 在这种自引用下会让 hwui 的 `RenderNode::prepareTreeImpl` 无限递归、直接栈溢出崩进程
 * （设置页 / 下载页必现）。等我们把页面拆成「内容层 backdrop + 独立控件层」再启用。
 */
@Composable
fun ErosTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: LayerBackdrop? = null,
    modifier: Modifier = Modifier,
) {
    TabRow(
        tabs = tabs,
        selectedTabIndex = selectedIndex,
        onTabSelected = onSelect,
        modifier = modifier,
    )
}

/**
 * 下拉选项行：玻璃可用时走 `GlassDropdownPreference`（弹层是玻璃面板），否则退回
 * `OverlayDropdownPreference`。
 *
 * 注意传进来的 `backdrop` 只作为「页面背景」来源；玻璃弹层不直接采样它 —— 同 [ErosTabRow]
 * 的自引用原因，弹层采样包含自身的层会栈溢出。这里刻意让它用不透明底色。
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
            backdrop = null,
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
