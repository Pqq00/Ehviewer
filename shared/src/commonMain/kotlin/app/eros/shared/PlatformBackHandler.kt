// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

import androidx.compose.runtime.Composable

/**
 * 平台返回键处理。
 *
 * Compose Multiplatform 1.12 里没有通用的 BackHandler，所以按平台实现：
 * Android 走 androidx.activity 的 BackHandler，桌面先空实现（后续接 Esc）。
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
