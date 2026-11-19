// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

/** 桌面端没有计费网络的概念，预取限 Wi-Fi 在这里恒为「不限制」。 */
actual fun isMeteredNetwork(): Boolean = false
