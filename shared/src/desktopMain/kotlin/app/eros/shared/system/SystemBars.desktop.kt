// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.system

/** desktop 只作为单测宿主，没有系统状态栏。 */
actual fun setStatusBarHidden(hidden: Boolean) = Unit
