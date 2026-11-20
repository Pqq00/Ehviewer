// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import app.eros.shared.settings.AppSettings

import androidx.compose.runtime.Composable
import app.eros.shared.reader.ReaderFit
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** 设置页「阅读」一节的控件。 */
@Composable
internal fun SettingsReadingSection() {
    ErosDropdownPreference(
        title = "预加载页数",
        items = listOf("0 页", "3 页", "5 页", "10 页"),
        selectedIndex = when (AppSettings.readerPreloadPages) {
            0 -> 0
            5 -> 2
            10 -> 3
            else -> 1
        },
        onSelectedIndexChange = {
            AppSettings.readerPreloadPages = listOf(0, 3, 5, 10)[it]
        },
    )
    ErosDropdownPreference(
        title = "位图缓存",
        items = listOf("32 MB", "64 MB", "128 MB", "256 MB", "512 MB"),
        selectedIndex = when (AppSettings.bitmapCacheMb) {
            32 -> 0
            64 -> 1
            256 -> 3
            512 -> 4
            else -> 2
        },
        onSelectedIndexChange = {
            AppSettings.bitmapCacheMb = listOf(32, 64, 128, 256, 512)[it]
        },
    )
    SwitchPreference(
        title = "左右点按翻页",
        checked = AppSettings.readerTapPaging,
        onCheckedChange = { AppSettings.readerTapPaging = it },
    )
    ErosDropdownPreference(
        title = "页面适应",
        items = ReaderFit.entries.map { it.label },
        selectedIndex = ReaderFit.fromOrdinal(AppSettings.readerFit).ordinal,
        onSelectedIndexChange = { AppSettings.readerFit = it },
    )
    ErosDropdownPreference(
        title = "自动隐藏顶底栏",
        items = listOf("不隐藏", "2 秒", "3 秒", "5 秒"),
        selectedIndex = when (AppSettings.readerAutoHideSeconds) {
            0 -> 0
            2 -> 1
            5 -> 3
            else -> 2
        },
        onSelectedIndexChange = {
            AppSettings.readerAutoHideSeconds = listOf(0, 2, 3, 5)[it]
        },
    )
    SwitchPreference(
        title = "音量键翻页",
        checked = AppSettings.readerVolumeKeys,
        onCheckedChange = { AppSettings.readerVolumeKeys = it },
    )
    SwitchPreference(
        title = "双击缩放",
        checked = AppSettings.readerDoubleTapZoom,
        onCheckedChange = { AppSettings.readerDoubleTapZoom = it },
    )
    ErosDropdownPreference(
        title = "最大缩放",
        items = listOf("2×", "3×", "4×", "5×", "6×"),
        selectedIndex = (AppSettings.readerMaxZoom - 2).coerceIn(0, 4),
        onSelectedIndexChange = { AppSettings.readerMaxZoom = it + 2 },
    )
    SwitchPreference(
        title = "按画廊记忆模式",
        checked = AppSettings.readerPerGalleryMode,
        onCheckedChange = { AppSettings.readerPerGalleryMode = it },
    )
}
