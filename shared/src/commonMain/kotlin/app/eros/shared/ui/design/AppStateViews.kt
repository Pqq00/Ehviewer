// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 列表「加载失败」占位：整块可点重试。
 *
 * 替代各页面各写一遍的 `Box { Text("加载失败，点按重试") }`。
 */
@Composable
fun AppErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = "加载失败，点按重试",
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clickable(onClick = onRetry),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MiuixTheme.textStyles.body2)
    }
}
