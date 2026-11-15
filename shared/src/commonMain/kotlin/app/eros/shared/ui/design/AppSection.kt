// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 标准分组：`SmallTitle` 标题 + 一张 `Card`。
 *
 * 设置页、筛选页、账号页的每个分组都用它，保证「标题 — 卡片 — 左右留白」一致。
 * 内容直接放进 Card（`ColumnScope`），preference 组件无需再套 Column。
 *
 * 卡片底部留 12dp，与 miuix 官方设置页示例的
 * `Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp))` 节奏一致。
 */
@Composable
fun AppSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        SmallTitle(title)
        AppCard { content() }
    }
}

/**
 * 标准卡片：统一左右 [AppDimens.PagePadding] 与圆角。
 *
 * 需要自定义内部 padding 时传 [insideMargin]；需要点击时在 [modifier] 上加 clickable。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = AppDimens.PagePadding)
            .then(modifier),
        insideMargin = insideMargin,
        content = content,
    )
}

/** 一段说明文字（卡片外，弱化色）。 */
@Composable
fun AppHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = AppDimens.PagePadding + 12.dp, vertical = 6.dp),
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
}
