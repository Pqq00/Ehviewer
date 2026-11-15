// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统一尺寸 token：8 个页面（首页/排行/收藏/下载/设置/历史/搜索/账号）共用。
 *
 * 规则：页面级左右边距 12；网格间距 12；列表间距 8；段间距 16；chips 高 36。
 * 只允许 `ErosPullRefreshGrid` / `ErosListPage` 这类容器消费 `innerPadding`，
 * 页面里不要重复加 top padding。
 */
object ErosDimens {
    val pagePadding: Dp = 12.dp
    val topContentGap: Dp = 12.dp
    val sectionGap: Dp = 16.dp
    val listGap: Dp = 8.dp
    val gridGap: Dp = 12.dp
    val chipHeight: Dp = 36.dp
    val chipHorizontalPadding: Dp = 12.dp
    val chipVerticalPadding: Dp = 8.dp
    val cardInside: Dp = 8.dp
}

/** 空态：标题 + 说明 + 可选动作（居中，占满可用高度）。 */
@Composable
fun ErosEmptyState(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                Text(
                    actionLabel,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MiuixTheme.textStyles.button,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 错误态：统一「加载失败 + 原因 + 点按重试」。 */
@Composable
fun ErosErrorState(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onRetry),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text("加载失败，点按重试", style = MiuixTheme.textStyles.body1)
            if (!message.isNullOrBlank()) {
                Text(
                    message,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 列表/网格通用加载态：居中 spinner（骨架由各页的 SkeletonCard 负责）。 */
@Composable
fun ErosLoadingState(
    modifier: Modifier = Modifier,
    label: String = "正在加载…",
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}
