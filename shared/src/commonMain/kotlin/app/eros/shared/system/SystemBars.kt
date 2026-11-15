// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.system

/**
 * 隐藏/恢复系统状态栏。
 *
 * 阅读器用：隐藏后页面可以顶到屏幕最上沿，退出阅读器或关掉设置时再恢复。
 * 只动状态栏，导航栏不受影响（手势条区域本来就不占内容高度）。
 */
expect fun setStatusBarHidden(hidden: Boolean)
