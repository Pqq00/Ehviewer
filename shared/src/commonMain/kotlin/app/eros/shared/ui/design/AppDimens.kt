// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.design

import androidx.compose.ui.unit.dp

/**
 * 设计层尺寸令牌。
 *
 * 页面不再直接写 `12.dp` / `16.dp` 这类散落数字：统一从这里取，
 * 以后调整密度、适配平板或统一圆角只需要改一处。
 */
object AppDimens {
    /** 页面左右留白（列表、卡片、分组）。 */
    val PagePadding = 12.dp

    /** 页面底部额外留白（避开底栏/手势条）。 */
    val PageBottomExtra = 24.dp
}
