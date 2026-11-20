// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import app.eros.shared.settings.AppSettings

import androidx.compose.runtime.Composable
import app.eros.shared.data.cache.CacheScope
import app.eros.shared.data.cache.CacheUsage
import app.eros.shared.isDebugBuild
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** 设置页「下载与存储」一节：并发、预取开关与各缓存层的清理入口。 */
@Composable
internal fun SettingsStorageSection(
    cacheUsage: CacheUsage,
    onClearScope: (CacheScope) -> Unit,
) {
    ErosDropdownPreference(
        title = "同时下载",
        items = listOf("1 个", "2 个", "3 个"),
        selectedIndex = (AppSettings.downloadGalleryConcurrency - 1).coerceIn(0, 2),
        onSelectedIndexChange = {
            AppSettings.downloadGalleryConcurrency = it + 1
        },
    )
    ErosDropdownPreference(
        title = "单画廊并发",
        items = listOf("1 页", "2 页", "3 页", "5 页"),
        selectedIndex = when (AppSettings.downloadPageConcurrency) {
            1 -> 0
            2 -> 1
            5 -> 3
            else -> 2
        },
        onSelectedIndexChange = {
            AppSettings.downloadPageConcurrency = listOf(1, 2, 3, 5)[it]
        },
    )
    // 缓存分区与「仅 Wi-Fi 预取」目前只作为调试/验证入口，发行版不暴露。
    if (isDebugBuild) {
        SwitchPreference(
            title = "仅 Wi-Fi 预取",
            summary = "移动数据下不预取封面与详情，滑到哪加载到哪",
            checked = AppSettings.prefetchWifiOnly,
            onCheckedChange = { AppSettings.prefetchWifiOnly = it },
        )
        CacheScope.entries.forEach { scope ->
            val summary = if (scope == CacheScope.ImageLoader) {
                if (cacheUsage.imageLoaderKnown) {
                    "内存 " + formatCacheSize(cacheUsage.imageLoaderMemory) +
                        " · 磁盘 " + formatCacheSize(cacheUsage.imageLoaderDisk)
                } else {
                    "此平台不可用"
                }
            } else {
                formatCacheSize(cacheUsage.of(scope)) + " · " + scope.summary
            }
            ArrowPreference(
                title = scope.title,
                summary = summary,
                onClick = { onClearScope(scope) },
            )
        }
    }
}

/** 字节数 → 人类可读（MB/KB）。 */
private fun formatCacheSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
