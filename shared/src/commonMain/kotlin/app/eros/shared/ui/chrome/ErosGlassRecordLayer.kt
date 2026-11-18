// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.hippo.ehviewer.util.AppLog
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

/**
 * 玻璃记录层与玻璃栏的唯一约定。
 *
 * miuix 的玻璃控件靠 `LayerBackdrop` 采样「它下面已经画好的内容」。采样自引用会让
 * hwui 在 `RenderNode::prepareTreeImpl` 里无限递归，直接栈溢出崩进程 —— `e843247`
 * 那次「下载页长按闪退」就是这个原因（玻璃工具栏被摆进了它自己要采样的那一层）。
 *
 * 所以规则是：
 * 1. 页面内容用 [ErosGlassRecordLayer] 记录成一层（内部挂 `layerBackdrop`）；
 * 2. 玻璃栏 / 玻璃工具栏一律用 [ErosGlassBar] 包起来，**放在它要采样的那一层之外**。
 *
 * 这两条以前只写在四处注释里，现在由 API 承载：[ErosGlassBar] 会检查「你要采样的
 * backdrop 是不是某个祖先 [ErosGlassRecordLayer] 正在记录的层」，是就用 error 日志点名。
 * 新增页面照抄这两个调用即可，不需要再记规则。
 *
 * 分层契约（L0 窗口 chrome / L1 页面记录层 / L2 页面栏 / L3 浮层 / L4 全屏路由）见仓库根
 * `UI_LAYERS.md`；本文件只负责 L1 的记录与 L2/L3 的自采样守卫。
 */

/** 记录层登记项：名字只用于报错，方便定位是哪一层被自采样。 */
internal data class GlassRecordLayer(val name: String, val backdrop: LayerBackdrop)

/** 当前组合所在的记录层集合（玻璃栏放错位置时用来报错）。 */
internal val LocalGlassRecordLayers = staticCompositionLocalOf<List<GlassRecordLayer>> { emptyList() }

/**
 * 把页面内容记录成一层 backdrop，供玻璃栏 / 玻璃工具栏采样。
 *
 * 采样者（[ErosGlassBar]）必须放在这个调用**之外**，否则会采到自己。
 *
 * @param backdrop 记录层；null 时只做普通 Box（模糊不可用 / 页面不需要玻璃）。
 * @param layerName 层名（`UI_LAYERS.md` 里的 L1 名称），只用于自采样报错定位。
 */
@Composable
internal fun ErosGlassRecordLayer(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    layerName: String = "record layer",
    content: @Composable () -> Unit,
) {
    Box(
        modifier.then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
    ) {
        val enclosing = LocalGlassRecordLayers.current
        val layers =
            if (backdrop != null) enclosing + GlassRecordLayer(layerName, backdrop) else enclosing
        CompositionLocalProvider(LocalGlassRecordLayers provides layers) {
            content()
        }
    }
}

/**
 * 玻璃栏 / 玻璃工具栏的宿主标记。
 *
 * @param name 用于日志的栏名，例如 "app bottom bar"、"download selection toolbar"。
 * @param backdrop 这个栏要采样的层；如果它正是某个祖先记录层正在记录的层，
 *   说明栏被摆进了自己要采样的内容里 —— 真机上会在下一次渲染 prepare 时栈溢出。
 */
@Composable
internal fun ErosGlassBar(
    name: String,
    backdrop: LayerBackdrop?,
    content: @Composable () -> Unit,
) {
    if (backdrop != null) {
        val enclosing = LocalGlassRecordLayers.current.filter { it.backdrop === backdrop }
        if (enclosing.isNotEmpty()) {
            AppLog.e(
                "ErosGlassBar",
                "玻璃栏 \"$name\" 采样了包含自己的记录层 " +
                    enclosing.joinToString { "\"${it.name}\"" } +
                    "：hwui prepare 会递归栈溢出（见 e843247）。" +
                    "请把它移到该 ErosGlassRecordLayer 之外的兄弟节点。",
            )
        }
    }
    content()
}
