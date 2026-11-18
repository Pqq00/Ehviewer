// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.input.nestedscroll.nestedScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.eros.shared.filter.FilterMode
import app.eros.shared.filter.FilterRule
import app.eros.shared.filter.GalleryFilter
import app.eros.shared.settings.AppSettings
import app.eros.shared.system.showSystemToast
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 过滤规则子页：规则列表 + 右上角 + 底部滑出添加面板。
 * 从设置页搬出来，设置页只保留开关和入口。
 */
@Composable
fun FilterScreen(onBack: () -> Unit) {
    var rules by remember { mutableStateOf(AppSettings.filterRules) }
    var showAdd by remember { mutableStateOf(false) }
    var addText by remember { mutableStateOf("") }
    var addMode by remember { mutableStateOf(0) }

    fun persist(next: List<FilterRule>) {
        rules = next
        AppSettings.filterRules = next
    }

    ErosPageScaffold(
        title = "过滤规则",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showAdd = true }) {
                Icon(imageVector = MiuixIcons.Add, contentDescription = "添加规则")
            }
        },
    ) { innerPadding, scrollBehavior, _ ->
        Box(Modifier.fillMaxSize()) {
            if (rules.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有过滤规则，点右上角 + 添加", style = MiuixTheme.textStyles.body2)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rules.size) { index ->
                        val rule = rules[index]
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(rule.text, style = MiuixTheme.textStyles.body1)
                                    Text(
                                        rule.mode.label,
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { checked ->
                                        persist(rules.toMutableList().also { list ->
                                            list[index] = rule.copy(enabled = checked)
                                        })
                                    },
                                )
                                Text(
                                    "删除",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.error,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                        .clickable {
                                            persist((rules.toMutableList()).also { it.removeAt(index) })
                                            showSystemToast("已删除规则")
                                        },
                                )
                            }
                        }
                    }
                }
            }

            TrackOverlay(showAdd)
            ErosBottomSheet(
                backdrop = LocalPageBackdrop.current,
                show = showAdd,
                title = "添加过滤规则",
                onDismissRequest = { showAdd = false },
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    OverlayDropdownPreference(
                        title = "规则类型",
                        items = FilterMode.entries.map { it.label },
                        selectedIndex = addMode,
                        onSelectedIndexChange = { addMode = it },
                    )
                    TextField(
                        value = addText,
                        onValueChange = { addText = it },
                        label = "要屏蔽的内容",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { showAdd = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(),
                        ) { Text("取消", style = MiuixTheme.textStyles.button) }
                        Button(
                            onClick = {
                                val text = addText.trim()
                                val mode = FilterMode.entries[addMode]
                                showAdd = false
                                addText = ""
                                if (text.isBlank()) {
                                    showSystemToast("请输入内容")
                                } else {
                                    persist(rules + FilterRule(mode, text, true))
                                    showSystemToast("已添加：$text")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.primary,
                                contentColor = MiuixTheme.colorScheme.onPrimary,
                            ),
                        ) { Text("添加", style = MiuixTheme.textStyles.button) }
                    }
                }
            }
        }
    }
}
