// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.system

/** 系统级提示（Android 用 Toast，桌面打印到控制台）。 */
expect fun showSystemToast(message: String)
