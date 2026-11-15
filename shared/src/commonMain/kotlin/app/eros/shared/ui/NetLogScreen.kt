// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.eros.shared.diag.clearNetLog
import app.eros.shared.diag.exportNetLog
import app.eros.shared.diag.netLogEntries
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 网络日志子页：从设置页搬出来，避免设置页里内嵌一大段滚动日志。
 */
@Composable
fun NetLogScreen(onBack: () -> Unit) {
    var logs by remember { mutableStateOf(netLogEntries()) }
    var exportResult by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        while (true) {
            logs = netLogEntries()
            delay(800)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SmallTopAppBar(
                title = "网络日志",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { exportResult = exportNetLog() }) {
                        Text("导出", style = MiuixTheme.textStyles.footnote2)
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            if (logs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无日志（回到设置打开「网络日志」开关后开始记录）", style = MiuixTheme.textStyles.body2)
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(logs.size) { index ->
                        val entry = logs[index]
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    "${entry.time}  ${entry.line}",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = if (entry.isError) MiuixTheme.colorScheme.error
                                    else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }
                }
            }

            // 底部操作条
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { confirmClear = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(),
                ) { Text("清空", style = MiuixTheme.textStyles.button) }
                Button(
                    onClick = { logs = netLogEntries() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary,
                        contentColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                ) { Text("刷新", style = MiuixTheme.textStyles.button) }
            }
        }
    }

    ErosConfirmDialog(
        show = confirmClear,
        title = "清空网络日志",
        summary = "会删除当前记录的全部请求日志，不可恢复。",
        confirmText = "清空",
        onConfirm = {
            confirmClear = false
            clearNetLog()
            logs = emptyList()
            exportResult = null
        },
        onDismiss = { confirmClear = false },
    )

    exportResult?.let { msg ->
        ErosConfirmDialog(
            show = true,
            title = "导出完成",
            summary = msg,
            confirmText = "好",
            dismissText = "关闭",
            onConfirm = { exportResult = null },
            onDismiss = { exportResult = null },
        )
    }
}
