// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/**
 * 主题模式。ordinal 与 [ColorSchemeMode] 一致，便于直接映射。
 */
enum class ThemeModeEntry(val label: String, val mode: ColorSchemeMode) {
    SYSTEM("跟随系统", ColorSchemeMode.System),
    LIGHT("浅色", ColorSchemeMode.Light),
    DARK("深色", ColorSchemeMode.Dark),
    MONET_SYSTEM("壁纸·跟随", ColorSchemeMode.MonetSystem),
    MONET_LIGHT("壁纸·浅色", ColorSchemeMode.MonetLight),
    MONET_DARK("壁纸·深色", ColorSchemeMode.MonetDark),
}
