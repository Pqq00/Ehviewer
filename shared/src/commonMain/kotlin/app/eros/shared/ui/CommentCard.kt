// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.eros.shared.data.CommentUi
import app.eros.shared.data.GalleryRef
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 单条评论。
 *
 * 评论页用完整形态（正文可折叠、底部投票胶囊 + 引用/编辑）；详情页用 [compact]
 * 只显示前三行摘要，两张卡片的头像、昵称、徽章、时间排版共用同一套代码，避免样式漂移。
 *
 * @param onVoteUp/onVoteDown 为 null 或对应 `canVote*` 为 false 时对应箭头变淡且不响应。
 * @param onQuote 引用：把 `>>id` 前缀插进输入框（E-Hentai 没有回复线程，按惯例引用）。
 * @param onEdit 仅 `comment.editable` 为真时传入。
 * @param onOpenGallery 正文里的站内画廊链接被点击时的跳转；为 null 时统一交给系统浏览器。
 */
@Composable
internal fun CommentCard(
    comment: CommentUi,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onVoteUp: (() -> Unit)? = null,
    onVoteDown: (() -> Unit)? = null,
    onQuote: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onOpenGallery: ((GalleryRef) -> Unit)? = null,
) {
    // 折叠：默认收起，正文溢出时才显示「展开」；展开后显示「收起」。
    var expanded by remember(comment.id) { mutableStateOf(false) }
    var overflowing by remember(comment.id) { mutableStateOf(false) }
    val collapsedLines = if (compact) 3 else COLLAPSED_LINES

    Card(modifier = modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CommentHeader(comment)

            LinkedText(
                text = comment.text,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!expanded) overflowing = result.hasVisualOverflow
                },
                onOpenGallery = onOpenGallery,
            )
            if (overflowing) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }

            if (!compact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VotePill(
                        comment = comment,
                        onVoteUp = onVoteUp,
                        onVoteDown = onVoteDown,
                    )
                    Spacer(Modifier.weight(1f))
                    // id 为 0 的是上传者评论（站点注释：0 for uploader comment），引用它没有意义。
                    if (onQuote != null && comment.id > 0L) {
                        TextAction(text = "引用", onClick = onQuote)
                    }
                    if (onEdit != null) {
                        TextAction(text = "编辑", onClick = onEdit)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentHeader(comment: CommentUi) {
    val time = buildString {
        if (comment.time > 0) append(relativeTime(comment.time))
        if (comment.isEdited) {
            if (isNotEmpty()) append(" · ")
            append("已编辑")
        }
    }
    // 顶部对齐：时间固定在右上角，不会因为名字长短或垂直居中而上下浮动。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CommentAvatar(comment.user)
        // 名字区至少与头像同高并垂直居中，保证「头像中心 = 名字中心」。
        Row(
            modifier = Modifier.weight(1f).heightIn(min = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = comment.user.ifBlank { "匿名" },
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (comment.isUploader) {
                UploaderBadge()
            }
        }
        if (time.isNotEmpty()) {
            Text(
                text = time,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** 用户名取色相生成色块 + 首字，不请求任何网络图片（E-Hentai 没有头像）。 */
@Composable
private fun CommentAvatar(user: String) {
    val display = user.ifBlank { "匿" }
    val color = remember(user) {
        var hash = 0
        for (ch in user) hash = hash * 31 + ch.code
        Color.hsv((hash and 0x7fffffff) % 360f, 0.38f, 0.72f)
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = display.take(1),
            style = MiuixTheme.textStyles.footnote1,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun UploaderBadge() {
    Text(
        text = "上传者",
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** 投票胶囊：`[▲ | 43 | ▼]`，已投的一侧用 primary，不可投票的一侧变淡。 */
@Composable
private fun VotePill(
    comment: CommentUi,
    onVoteUp: (() -> Unit)?,
    onVoteDown: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MiuixTheme.colorScheme.secondary),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoteArrow(
            up = true,
            enabled = comment.canVoteUp && onVoteUp != null,
            active = comment.votedUp,
            onClick = { onVoteUp?.invoke() },
        )
        Text(
            text = comment.score.toString(),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 18.dp),
        )
        VoteArrow(
            up = false,
            enabled = comment.canVoteDown && onVoteDown != null,
            active = comment.votedDown,
            onClick = { onVoteDown?.invoke() },
        )
    }
}

@Composable
private fun VoteArrow(
    up: Boolean,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        active -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }
    Box(
        modifier = Modifier
            .size(width = 26.dp, height = 22.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(8.dp)) {
            val path = Path()
            if (up) {
                path.moveTo(size.width / 2f, 0f)
                path.lineTo(size.width, size.height)
                path.lineTo(0f, size.height)
            } else {
                path.moveTo(0f, 0f)
                path.lineTo(size.width, 0f)
                path.lineTo(size.width / 2f, size.height)
            }
            path.close()
            drawPath(path, tint)
        }
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 正文折叠行数（评论页）。 */
private const val COLLAPSED_LINES = 6
