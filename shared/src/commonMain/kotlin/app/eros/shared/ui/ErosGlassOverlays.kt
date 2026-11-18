// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import app.eros.shared.settings.AppSettings
import app.eros.shared.ui.effect.isShaderSupported

/**
 * 玻璃弹层（底部 sheet / 对话框）的统一入口。
 *
 * 关键约束：**backdrop 必须是「页面内容层」，不能包含弹层自身**。
 * 弹层由 `renderInRootScaffold = true` 挂在根 Scaffold 的浮层里，和页面内容层是兄弟节点，
 * 所以采样不会自引用（对比：页内 Tab 采样自身曾导致 hwui 递归栈溢出，见 ErosGlassParts.kt）。
 *
 * 拿不到页面 backdrop（例如弹层不在 ErosPageScaffold 里）或模糊不可用时，退回原来的
 * `OverlayBottomSheet` / `OverlayDialog`，外观与行为跟以前一致。
 *
 * 分层契约见仓库根 `UI_LAYERS.md`：本文件是 L3 浮层的唯一入口（页面不要直接调 miuix
 * `Overlay*`）。`backdrop` 必填：页面浮层传 `LocalPageBackdrop.current`（页面内容层），
 * 窗口级浮层传 `LocalChromeBackdrop.current`（整页层）；不要图省事传 null，否则玻璃降级。
 */
private fun glassOverlayAvailable(backdrop: Any?): Boolean =
    backdrop != null && AppSettings.barBlurEnabled && isShaderSupported()

@Composable
fun ErosBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    insideMargin: DpSize? = null,
    onDismissFinished: (() -> Unit)? = null,
    backdrop: LayerBackdrop?,
    content: @Composable () -> Unit,
) {
    if (glassOverlayAvailable(backdrop)) {
        if (insideMargin != null) {
            GlassBottomSheet(
                show = show,
                onDismissRequest = onDismissRequest,
                backdrop = backdrop,
                title = title,
                insideMargin = insideMargin,
                onDismissFinished = onDismissFinished,
                content = content,
            )
        } else {
            GlassBottomSheet(
                show = show,
                onDismissRequest = onDismissRequest,
                backdrop = backdrop,
                title = title,
                onDismissFinished = onDismissFinished,
                content = content,
            )
        }
    } else {
        if (insideMargin != null) {
            OverlayBottomSheet(
                show = show,
                onDismissRequest = onDismissRequest,
                title = title,
                insideMargin = insideMargin,
                onDismissFinished = onDismissFinished,
                content = content,
            )
        } else {
            OverlayBottomSheet(
                show = show,
                onDismissRequest = onDismissRequest,
                title = title,
                onDismissFinished = onDismissFinished,
                content = content,
            )
        }
    }
}

@Composable
fun ErosDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    summary: String? = null,
    modifier: Modifier = Modifier,
    onDismissFinished: (() -> Unit)? = null,
    backdrop: LayerBackdrop?,
    content: @Composable () -> Unit,
) {
    if (glassOverlayAvailable(backdrop)) {
        GlassDialog(
            show = show,
            onDismissRequest = onDismissRequest,
            backdrop = backdrop,
            modifier = modifier,
            title = title,
            summary = summary,
            onDismissFinished = onDismissFinished,
            content = content,
        )
    } else {
        OverlayDialog(
            show = show,
            onDismissRequest = onDismissRequest,
            title = title,
            summary = summary,
            modifier = modifier,
            onDismissFinished = onDismissFinished,
            content = content,
        )
    }
}
