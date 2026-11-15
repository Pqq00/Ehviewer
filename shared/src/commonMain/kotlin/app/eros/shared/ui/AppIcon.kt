// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter

/**
 * 应用图标。
 *
 * shared 模块读不到 app 模块的 mipmap 资源，所以由各平台入口注入：
 * Android 在 `MainActivity` 里用 `painterResource(R.mipmap.ic_eros)` 提供，
 * 桌面端不提供，界面回退到内置图标。
 */
val LocalAppIcon = staticCompositionLocalOf<Painter?> { null }
