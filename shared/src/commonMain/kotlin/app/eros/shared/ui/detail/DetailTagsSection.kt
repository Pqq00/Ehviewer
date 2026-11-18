// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.detail

import app.eros.shared.data.GalleryTitles
import com.hippo.ehviewer.util.AppLog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import app.eros.shared.data.EhApi
import app.eros.shared.data.GalleryDetailUi
import app.eros.shared.data.GalleryRef
import app.eros.shared.currentTimeMillis
import app.eros.shared.data.cache.DetailMemoryCache
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.data.cache.TagRecord
import app.eros.shared.data.EhError
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.PreviewThumbUi
import app.eros.shared.data.createEhApi
import app.eros.shared.data.isRetryable
import app.eros.shared.data.withEhError
import app.eros.shared.data.userMessage
import app.eros.shared.filter.FilterMode
import app.eros.shared.filter.FilterRule
import app.eros.shared.settings.AppSettings
import app.eros.shared.system.showSystemToast
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import app.eros.shared.ui.chrome.LocalAppBottomBarInset
import app.eros.shared.ui.TrackOverlay
import app.eros.shared.ui.ErosBottomSheet

@Composable
internal fun TagsSection(
    detail: GalleryDetailUi,
    onSearchTag: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        insideMargin = PaddingValues(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            detail.tags.forEach { group ->
                if (group.tags.isEmpty()) return@forEach
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GroupTitleChip(namespace = group.namespace)
                    group.tags.forEach { tag ->
                        TagChip(
                            tag = tag,
                            onClick = { onSearchTag("${group.namespace}:$tag") },
                            onLongClick = { selected = group.namespace to tag },
                        )
                    }
                }
            }
        }
    }

    val tagSelection = selected
    val raw = tagSelection?.let { "${it.first}:${it.second}" } ?: ""
    TrackOverlay(tagSelection != null)
    ErosBottomSheet(
        show = tagSelection != null,
        title = raw,
        onDismissRequest = { selected = null },
        onDismissFinished = { },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TagSheetAction("搜索这个标签") {
                selected = null
                onSearchTag(raw)
            }
            TagSheetAction("复制标签") {
                clipboard.setText(AnnotatedString(raw))
                selected = null
                showSystemToast("已复制")
            }
            TagSheetAction("加入监听") {
                selected = null
                scope.launch {
                    val ok = runCatching {
                        withContext(Dispatchers.IO) {
                            createEhApi().addWatchedTag(raw, watched = true, hidden = false)
                        }
                    }.isSuccess
                    showSystemToast(if (ok) "已加入监听" else "操作失败，请确认已登录")
                }
            }
            TagSheetAction("加入屏蔽") {
                AppSettings.filterRules =
                    AppSettings.filterRules + FilterRule(FilterMode.TAG, raw)
                selected = null
                showSystemToast("已加入屏蔽")
            }
        }
    }
}

private fun namespaceLabel(namespace: String): String = when (namespace) {
    "language" -> "语言"
    "parody" -> "作品"
    "character" -> "角色"
    "group" -> "团队"
    "artist" -> "画师"
    "circle" -> "社团"
    "cosplay" -> "角色扮演"
    "female" -> "女性"
    "male" -> "男性"
    "mixed" -> "混合"
    "other" -> "其他"
    "series" -> "系列"
    "event" -> "活动"
    "uploader" -> "上传者"
    "reclass" -> "重分类"
    "temp" -> "临时"
    else -> namespace
}

private fun namespaceColor(namespace: String): Color = when (namespace) {
    "language" -> Color(0xFFB26A00)
    "parody" -> Color(0xFF1565C0)
    "character" -> Color(0xFF6A1B9A)
    "female" -> Color(0xFFAD1457)
    "male" -> Color(0xFF283593)
    "mixed" -> Color(0xFF00695C)
    "other" -> Color(0xFF5D5D5D)
    "artist" -> Color(0xFF2E7D32)
    "circle" -> Color(0xFF6D4C41)
    "series" -> Color(0xFF0277BD)
    "event" -> Color(0xFFE65100)
    "uploader" -> Color(0xFF455A64)
    "cosplay" -> Color(0xFF7B1FA2)
    "group" -> Color(0xFF00838F)
    else -> Color(0xFF5D5D5D)
}

@Composable
private fun GroupTitleChip(
    namespace: String,
) {
    val color = namespaceColor(namespace)
    Text(
        text = namespaceLabel(namespace),
        style = MiuixTheme.textStyles.footnote2,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .squircleSurface(
                color = color.copy(alpha = 0.16f),
                cornerRadius = 8.dp,
            )
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
private fun TagChip(
    tag: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val revision = app.eros.shared.tags.TagDatabase.revision
    val translation = remember(tag, AppSettings.tagTranslation, revision) {
        if (AppSettings.tagTranslation) {
            com.hippo.ehviewer.client.EhTagDatabase.translate(tag)
        } else {
            null
        }
    }
    Text(
        text = translation ?: tag,
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.primary,
        maxLines = 1,
        modifier = modifier
            .squircleSurface(
                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.10f),
                cornerRadius = 8.dp,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
private fun TagSheetAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body1,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = null)
            .padding(vertical = 14.dp),
    )
}

/** 评论：Card 内前 3 条摘要 + 查看全部（卡片本身复用评论页的 [CommentCard]）。 */
