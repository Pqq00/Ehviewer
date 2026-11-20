// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import app.eros.shared.settings.AppSettings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** 设置页「外观」一节的控件。内容与拆分前逐行一致，只是搬到了独立文件。 */
@Composable
internal fun SettingsAppearanceSection() {
    SwitchPreference(
        title = "模糊封面",
        checked = AppSettings.blurCovers,
        onCheckedChange = { AppSettings.blurCovers = it },
    )
    ErosDropdownPreference(
        title = "列表布局",
        // 持久化值仍是 classic / download。
        items = listOf("网格", "列表"),
        selectedIndex = if (AppSettings.cardStyle == "download") 1 else 0,
        onSelectedIndexChange = {
            AppSettings.cardStyle = if (it == 1) "download" else "classic"
        },
    )
    ErosDropdownPreference(
        title = "标题语言",
        items = listOf("英文优先", "日文优先"),
        selectedIndex = AppSettings.galleryTitleMode,
        onSelectedIndexChange = { AppSettings.galleryTitleMode = it },
    )
    ErosDropdownPreference(
        title = "主题模式",
        items = ThemeModeEntry.entries.map { it.label },
        selectedIndex = AppSettings.themeMode,
        onSelectedIndexChange = { AppSettings.themeMode = it },
    )
    ErosDropdownPreference(
        title = "导航栏样式",
        items = NavBarStyleEntry.entries.map { it.label },
        selectedIndex = AppSettings.navBarStyle,
        onSelectedIndexChange = { AppSettings.navBarStyle = it },
    )
    // 「导航项显示」只对固定/悬浮两种原生样式生效；iOS 与玻璃样式的图标与文字恒定显示
    AnimatedVisibility(visible = AppSettings.navBarStyle != 2 && AppSettings.navBarStyle != 3) {
        ErosDropdownPreference(
            title = "导航项显示",
            items = NavBarModeEntry.entries.map { it.label },
            selectedIndex = AppSettings.navBarMode,
            onSelectedIndexChange = { AppSettings.navBarMode = it },
        )
    }
    // 只在会真正触发侧边导航栏的宽度（≥600dp）上暴露这个开关，
    // 手机上它永远命中不了，放着只会让人误以为能开。
    AnimatedVisibility(visible = rememberNavigationRailBreakpointReached()) {
        SwitchPreference(
            title = "侧边导航栏",
            checked = AppSettings.navRail,
            onCheckedChange = { AppSettings.navRail = it },
        )
    }
    SwitchPreference(
        title = "栏模糊",
        summary = "Android 13+",
        checked = AppSettings.barBlurEnabled,
        onCheckedChange = { AppSettings.barBlurEnabled = it },
    )
    AnimatedVisibility(visible = AppSettings.barBlurEnabled) {
        ErosDropdownPreference(
            title = "顶栏模糊样式",
            items = BarBlurStyle.entries.map { it.label },
            selectedIndex = AppSettings.barBlurStyle,
            onSelectedIndexChange = { AppSettings.barBlurStyle = it },
        )
    }
}
