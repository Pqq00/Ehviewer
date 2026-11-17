// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.glass.ClockOutline
import top.yukonga.miuix.kmp.icon.extended.Search

/**
 * 顶栏左侧的账号入口。
 *
 * 首页、排行榜、收藏、下载、设置五个主页面共用同一套入口，
 * 抽出来是为了让位置、图标、无障碍文案保持一致。
 */
@Composable
fun AccountEntry(onOpenAccount: () -> Unit) {
    ErosBarIcon(onClick = onOpenAccount) {
        Icon(imageVector = MiuixIcons.Contacts, contentDescription = "账号")
    }
}

/**
 * 顶栏右侧的「浏览历史 + 搜索」两个入口，同样五个主页面共用。
 */
@Composable
fun RowScope.HistoryAndSearchEntries(
    onOpenHistory: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    ErosBarIcon(onClick = onOpenHistory) {
        Icon(imageVector = MiuixIcons.Glass.ClockOutline, contentDescription = "浏览历史")
    }
    ErosBarIcon(onClick = onOpenSearch) {
        Icon(imageVector = MiuixIcons.Search, contentDescription = "搜索")
    }
}
