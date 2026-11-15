// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

/**
 * 桌面端没有安装包信息，只用于跑 commonTest，这里给固定占位值。
 */
actual fun platformAppVersionName(): String = "desktop"

actual fun platformAppVersionCode(): Long = 0L
