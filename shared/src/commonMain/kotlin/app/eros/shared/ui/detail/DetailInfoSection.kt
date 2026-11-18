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
import app.eros.shared.ui.MetaIcon
import app.eros.shared.ui.MetaIconKind
import app.eros.shared.ui.RatingStars
import app.eros.shared.ui.RemoteImage
import app.eros.shared.ui.coverBlur
import app.eros.shared.ui.categoryName
import app.eros.shared.ui.categoryColor
import app.eros.shared.ui.Badge

@Composable
private fun languageLabel(raw: String?): String? = raw?.takeIf { it.isNotBlank() }?.let {
    when (it.lowercase()) {
        "chinese" -> "中文"
        "japanese" -> "日文"
        "english" -> "英文"
        "korean" -> "韩文"
        else -> it
    }
}

@Composable
private fun MetaCell(kind: MetaIconKind, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        MetaIcon(kind = kind, tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        Text(
            value,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 底行固定高度：徽章、上传者、评分垂直居中，避免不同卡片间位置漂移。 */
private val BottomRowHeight = 22.dp

/** 主标题行高（14sp/18sp）：用于按行数换算副标题可用行数。 */
private val MainTitleLineHeight = 18.sp

/** 评分槽固定宽度：星星与分数位数变化不影响其右边缘。 */
private val RatingSlotWidth = 108.dp

@Composable
internal fun GalleryInfoSection(
    detail: GalleryDetailUi,
    favoriteCount: Int,
    onSearchTag: (String) -> Unit,
    onSearchUploader: (String) -> Unit,
) {
    val language = languageLabel(detail.language)
    val clipboard = LocalClipboardManager.current
    // 详情页同时显示两种语言的标题：主标题（5 行）+ 副标题（1 行）。
    val mainTitle = GalleryTitles.primary(detail.title, detail.titleJpn)
    val subTitle = GalleryTitles.secondary(detail.title, detail.titleJpn)
    fun copyTitle(text: String) {
        clipboard.setText(AnnotatedString(text))
        showSystemToast("标题已复制")
    }
    Column {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            insideMargin = PaddingValues(8.dp),
            cornerRadius = CardDefaults.CornerRadius,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(128.dp)
                            .height(173.dp)
                            .squircleClip(cornerRadius = 10.dp)
                            .background(MiuixTheme.colorScheme.surfaceVariant),
                    ) {
                        RemoteImage(
                            url = detail.thumb,
                            contentScale = ContentScale.Crop,
                            failureText = "无封面",
                            modifier = Modifier.coverBlur(scale = 0.8f),
                        )
                    }
                    // 右栏与封面等高：标题贴顶、六项信息贴底，信息行底部与封面底部对齐。
                    Column(
                        modifier = Modifier.weight(1f).height(173.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // 标题区占满右栏剩余高度：主标题优先用满（最多 5 行），余下的高度全部给副标题。
                        Column(modifier = Modifier.weight(1f)) {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                // 实测主标题行数，再按剩余高度换算副标题可显示的行数。
                                var mainLines by remember(mainTitle) { mutableIntStateOf(5) }
                                val subLines = (
                                    (maxHeight - 18.dp * mainLines) / 16.dp
                                    ).toInt().coerceIn(1, 8)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        mainTitle,
                                        style = MiuixTheme.textStyles.title4.copy(
                                            fontSize = 14.sp,
                                            lineHeight = MainTitleLineHeight,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        maxLines = 5,
                                        overflow = TextOverflow.Ellipsis,
                                        onTextLayout = { mainLines = it.lineCount },
                                        modifier = Modifier.clickable { copyTitle(mainTitle) },
                                    )
                                    subTitle?.let { title ->
                                        Text(
                                            title,
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                            maxLines = subLines,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable { copyTitle(title) },
                                        )
                                    }
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row {
                                MetaCell(MetaIconKind.Globe, language ?: "-", Modifier.weight(1f))
                                MetaCell(
                                    MetaIconKind.Disk,
                                    detail.size?.takeIf { it.isNotBlank() } ?: "-",
                                    Modifier.weight(1f),
                                )
                            }
                            Row {
                                MetaCell(
                                    MetaIconKind.Star,
                                    if (detail.ratingCount > 0) "${detail.ratingCount}" else "-",
                                    Modifier.weight(1f),
                                )
                                MetaCell(MetaIconKind.Heart, "$favoriteCount", Modifier.weight(1f))
                            }
                            Row {
                                MetaCell(MetaIconKind.Doc, "${detail.pages}", Modifier.weight(1f))
                                MetaCell(
                                    MetaIconKind.Clock,
                                    detail.posted?.takeIf { it.isNotBlank() }?.substringBefore(" ") ?: "-",
                                    Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                // 底行：高度固定，保证评分位置不随徽章/上传者长度漂移。
                Row(
                    modifier = Modifier.fillMaxWidth().height(BottomRowHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categoryName(detail.category)?.let {
                        Badge(
                            text = it,
                            containerColor = categoryColor(detail.category),
                            outerPadding = 0.dp,
                        )
                    }
                    detail.uploader?.takeIf { it.isNotBlank() }?.let { uploader ->
                        // 居中显示；clickable 只包文字，点击热区 = 文字本身。
                        Box(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                uploader,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { onSearchUploader(uploader) },
                            )
                        }
                    }
                    if (detail.uploader.isNullOrBlank()) Spacer(Modifier.weight(1f))
                    // 固定宽度 + 末端对齐：星星数量/分数位数变化都不会移动它。
                    Row(
                        modifier = Modifier.widthIn(min = RatingSlotWidth),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    ) {
                        if (detail.rating > 0f) {
                            RatingStars(
                                rating = detail.rating,
                                tint = Color(0xFFF6A821),
                                starSize = 12.dp,
                            )
                            Text(
                                String.format(Locale.US, "%.2f", detail.rating),
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}


/** 收藏夹选择弹层的一行。 */
@Composable
internal fun FavCatRow(
    name: String,
    count: Int,
    selected: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = name,
            style = MiuixTheme.textStyles.body1,
            color = if (destructive) {
                MiuixTheme.colorScheme.error
            } else {
                MiuixTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count >= 0) {
            Text(
                text = count.toString(),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = "当前收藏夹",
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 标签：收起时展示前 8 个 chips + 内联「+N」，展开后全部 chips + 「收起」。 */
