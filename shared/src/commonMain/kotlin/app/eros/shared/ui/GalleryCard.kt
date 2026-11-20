// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import app.eros.shared.data.GalleryTitles
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.eros.shared.data.DetailSeedStore
import app.eros.shared.data.GalleryUi
import app.eros.shared.download.DownloadedStore
import app.eros.shared.settings.AppSettings
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 信息流卡片：封面 + 标题 + 角标。
 *
 * 角标：
 *  - 左上：分类（按分类着色）
 *  - 右上：语言两字母码（ZH / EN …）
 *  - 左下：页数（`32P`）
 *  - 右下：已下载 / 已读 ✓
 *  - 底部：阅读进度条（读到一半时）
 *
 * 封面按卡片实际像素尺寸请求；加载完成前只显示同尺寸骨架，
 * 完成后封面、角标、标题一起淡入。
 */
@Composable
fun GalleryCard(
    gallery: GalleryUi,
    /** 在网格里的位置，用于入场错峰（同一屏内 0..5 依次冒出来）。 */
    index: Int = 0,
    /** 列表正在滚动/惯性滑动：此时不显示按压反馈，也暂停模糊以保证滑动帧率。 */
    scrolling: Boolean = false,
    onClick: () -> Unit = {},
) {
    if (AppSettings.cardStyle == "download") {
        GalleryCardDownloadStyle(
            gallery = gallery,
            index = index,
            scrolling = scrolling,
            onClick = onClick,
        )
        return
    }
    // 「✓」只在真正读到最后一页后出现；仅打开过详情页不算。
    val finished = ReadStore.isFinished(gallery.gid)
    val readProgress = ReadStore.progressOf(gallery.gid)
    // 只在"读到一半"时显示进度条：0 页和读完都不显示。
    val partialProgress = readProgress
        ?.takeIf { (page, pages) -> pages > 0 && page > 0 && page < pages }
        ?.let { (page, pages) -> page.toFloat() / pages }
    val downloaded = DownloadedStore.isDownloaded(gallery.gid)
    val language = remember(gallery.tags, gallery.title) {
        languageOf(gallery.tags, gallery.title)
    }
    val category = categoryName(gallery.category)
    val aspect = when (AppSettings.coverAspect) {
        1 -> 0.8f
        2 -> 1f
        else -> 0.75f
    }
    val titleLines = AppSettings.titleLines
    var showSheet by remember { mutableStateOf(false) }
    // 标题还没解析出来 = 这一条还不能用：不给点击、不给按压反馈。
    val canOpen = gallery.title.isNotBlank()

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(8.dp),
        // 滚动中或数据未就绪时不显示按压反馈；长按弹窗打开时保持"按住"状态（不松手回弹）
        pressFeedbackType = if (scrolling || !canOpen) PressFeedbackType.None else PressFeedbackType.Sink,
        showIndication = !scrolling && canOpen,
        holdDownState = showSheet,
        onClick = {
            if (canOpen) {
                DetailSeedStore.put(gallery)
                onClick()
            }
        },
        onLongPress = { if (canOpen) showSheet = true },
    ) {
        val thumb = gallery.thumb
        val density = LocalDensity.current
        // 兜底尺寸：约束不可用时才使用，正常情况下直接按列宽请求，避免二次请求。
        val fallback = with(density) { 190.dp.roundToPx() }
        val coverState = rememberGalleryCoverState(thumb)
        val revealed = thumb == null || coverState.revealed
        // 入场动画与下载卡共用同一套（GalleryCardParts.rememberCardEntrance）：
        // 淡入 + 缩放 + 上滑，错峰最多 5 个位置，每个 gid 只播一次。
        val entrance = rememberCardEntrance(gallery.gid, index, revealed)

    
        Box(Modifier.fillMaxWidth()) {
            if (entrance.skeletonAlpha > 0.01f) {
                CardSkeleton(
                    aspect = aspect,
                    modifier = Modifier.graphicsLayer { alpha = entrance.skeletonAlpha },
                )
            }
            Column(Modifier.cardEntrance(entrance.progress)) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .squircleClip(cornerRadius = 10.dp)
                        .background(MiuixTheme.colorScheme.surfaceVariant),
                ) {
                    if (thumb != null) {
                        val measuredWidth = constraints.maxWidth
                        val requestWidth = if (measuredWidth > 0) measuredWidth else fallback
                        val requestHeight = if (measuredWidth > 0) {
                            (measuredWidth / aspect).roundToInt().coerceAtLeast(1)
                        } else {
                            fallback * 4 / 3
                        }
                        GalleryCoverImage(
                            state = coverState,
                            thumb = thumb,
                            requestWidth = requestWidth,
                            requestHeight = requestHeight,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    if (coverState.failed) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { coverState.retry() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "封面加载失败 · 点按重试",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }

                    // 左上：分类（按分类着色）
                    if (category != null) {
                        Badge(
                            text = category,
                            modifier = Modifier.align(Alignment.TopStart),
                            containerColor = categoryColor(gallery.category),
                        )
                    }

                    // 右上：语言（两字母码），位置固定，不随「已读 ✓」出现而偏移
                    if (language != null) {
                        Badge(text = language, modifier = Modifier.align(Alignment.TopEnd))
                    }

                    // 左下：页数
                    if (gallery.pages > 0) {
                        Badge(
                            text = "${gallery.pages}P",
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }

                    // 右下：已下载 / 已读
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (downloaded) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(Color(0xFF2E7D32), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Download,
                                    contentDescription = "已下载",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        if (finished) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(MiuixTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Ok,
                                    contentDescription = "已读",
                                    tint = MiuixTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }

                if (partialProgress != null) {
                    // 封面与标题之间的细进度条：左右缩进 8dp，2dp 高，圆角 1dp。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(partialProgress)
                                .fillMaxHeight()
                                .background(MiuixTheme.colorScheme.primary),
                        )
                    }
                }

                Row(modifier = Modifier.padding(top = if (partialProgress != null) 2.dp else 6.dp)) {
                    RegionCrossfade(
                        revealed = canOpen,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            // 标题未解析出来：用与标题同高的骨架条占位，位置与真标题一致。
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SkeletonBar(
                                    height = 14.dp,
                                    modifier = Modifier.fillMaxWidth(0.92f),
                                    animated = !scrolling,
                                )
                                if (titleLines > 1) {
                                    SkeletonBar(
                                        height = 14.dp,
                                        modifier = Modifier.fillMaxWidth(0.55f),
                                        animated = !scrolling,
                                    )
                                }
                            }
                        },
                        content = {
                            Text(
                                text = GalleryTitles.primary(gallery.title, gallery.titleJpn),
                                style = MiuixTheme.textStyles.footnote1,
                                maxLines = titleLines,
                                overflow = TextOverflow.Ellipsis,
                                color = if (finished) {
                                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    TrackOverlay(showSheet)
    GalleryCardActions(
        show = showSheet,
        gallery = gallery,
        onDismiss = { showSheet = false },
    )
}

