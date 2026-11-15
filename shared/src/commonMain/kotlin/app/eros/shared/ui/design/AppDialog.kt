// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.design

import androidx.compose.runtime.Composable
import app.eros.shared.ui.ErosConfirmDialog

/**
 * 设计层通用二次确认弹窗。
 *
 * 内部仍用 [ErosConfirmDialog]（Compose `Dialog` 独立窗口，避免 miuix `OverlayDialog`
 * 在 NavDisplay 子路由里拿不到 popup 宿主）；对外只暴露设计层的命名。
 */
@Composable
fun AppConfirmDialog(
    show: Boolean,
    title: String,
    summary: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "取消",
) {
    ErosConfirmDialog(
        show = show,
        title = title,
        summary = summary,
        confirmText = confirmText,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissText = dismissText,
    )
}
