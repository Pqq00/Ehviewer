// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import app.eros.shared.data.GalleryTitles
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eros.shared.data.GalleryUi
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 带失败占位的远程图片：加载失败或地址为空时显示灰底 + 短文案。
 */
@Composable
internal fun RemoteImage(
    url: String?,
    contentScale: ContentScale,
    failureText: String = "加载失败",
    modifier: Modifier = Modifier,
) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank() || failed) {
            Text(
                failureText,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize().then(modifier),
            )
        }
    }
}

/** 首屏种子头部：仅用列表已知信息渲染（详情未返回时）。 */
@Composable
internal fun SeedHeader(seed: GalleryUi, showPlaceholders: Boolean = true) {
    val placeholder = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)
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
                            url = seed.thumb,
                            contentScale = ContentScale.Crop,
                            failureText = "无封面",
                            modifier = Modifier.coverBlur(scale = 0.8f),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f).height(173.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            GalleryTitles.primary(seed.title, seed.titleJpn),
                            style = MiuixTheme.textStyles.title4.copy(
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (showPlaceholders) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(3) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.weight(1f).height(16.dp).squircleClip(4.dp).background(placeholder))
                                        Box(Modifier.weight(1f).height(16.dp).squircleClip(4.dp).background(placeholder))
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (showPlaceholders) {
                        Box(Modifier.width(56.dp).height(20.dp).squircleClip(10.dp).background(placeholder))
                    }
                    seed.uploader?.takeIf { it.isNotBlank() }?.let { uploader ->
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
                            )
                        }
                    }
                    if (seed.uploader.isNullOrBlank()) Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.widthIn(min = RatingSlotWidth),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    ) {
                        if (showPlaceholders) {
                            repeat(5) {
                                Box(Modifier.width(12.dp).height(12.dp).squircleClip(6.dp).background(placeholder))
                            }
                            Box(Modifier.width(40.dp).height(14.dp).squircleClip(4.dp).background(placeholder))
                        }
                    }
                }
            }
        }
    }
}

/** 底行固定高度：徽章/上传者/评分的垂直中心恒定。 */
private val BottomRowHeight = 22.dp

/** 评分槽最小宽度：星星与分数位数变化不会移动右边缘。 */
private val RatingSlotWidth = 108.dp

/** 详情加载骨架：信息卡 + 两个内容卡，与页面卡片同宽同圆角。 */
@Composable
internal fun DetailSkeleton(modifier: Modifier = Modifier, includeInfo: Boolean = true) {
    val base = MiuixTheme.colorScheme.surfaceVariant
    val highlight = MiuixTheme.colorScheme.surfaceContainerHigh
    val alpha by rememberInfiniteTransition(label = "detailSkeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "detailSkeletonAlpha",
    )
    val brush = Brush.linearGradient(
        listOf(
            base.copy(alpha = alpha),
            highlight.copy(alpha = alpha * 0.6f),
            base.copy(alpha = alpha),
        ),
    )
    BoxWithConstraints(modifier) {
        // 宽屏一行能塞更多 chip，占位数量跟着变，避免标签卡右侧留一大块空白。
        val chipPattern = listOf(72.dp, 96.dp, 64.dp, 88.dp, 56.dp, 80.dp, 104.dp, 60.dp)
        val chipPerRow = (((maxWidth - 24.dp) + 8.dp) / 88.dp).toInt().coerceIn(3, 8)
        val chipCount = chipPerRow * 5
        // 详情加载态在 verticalScroll 里，高度是内容高度、不会自动拉伸，
        // 所以按窗口高度反算还差几张评论卡，把下方空白补满。
        val extraCommentCards = detailSkeletonExtraCards(
            windowHeight = rememberWindowHeightDp(),
            hasInfoCard = includeInfo,
            tagRows = 5,
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (includeInfo) {
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier
                                .width(128.dp)
                                .height(173.dp)
                                .squircleClip(cornerRadius = 10.dp)
                                .background(brush),
                        )
                        Column(
                            Modifier.weight(1f).height(173.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.fillMaxWidth(0.95f).height(16.dp).squircleClip(6.dp).background(brush))
                                Box(Modifier.fillMaxWidth(0.7f).height(16.dp).squircleClip(6.dp).background(brush))
                                Box(Modifier.fillMaxWidth(0.5f).height(12.dp).squircleClip(6.dp).background(brush))
                                Box(Modifier.fillMaxWidth(0.85f).height(12.dp).squircleClip(6.dp).background(brush))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(3) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.weight(1f).height(16.dp).squircleClip(4.dp).background(brush))
                                        Box(Modifier.weight(1f).height(16.dp).squircleClip(4.dp).background(brush))
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.width(56.dp).height(20.dp).squircleClip(10.dp).background(brush))
                        Spacer(Modifier.weight(1f))
                        Row(
                            modifier = Modifier.widthIn(min = RatingSlotWidth),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        ) {
                            Box(Modifier.width(84.dp).height(14.dp).squircleClip(4.dp).background(brush))
                        }
                    }
                }
            }
        }
        // 标签卡：详情返回前一律用占位条。这里不能拿种子里的真实标签来画 ——
        // 那样会出现「上半张卡是骨架条、下半张卡已经是真标签」的混搭状态，
        // 用户看到的就是骨架里先冒出了标签。
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(chipCount) { index ->
                    Box(
                        Modifier
                            .width(chipPattern[index % chipPattern.size])
                            .height(26.dp)
                            .squircleClip(13.dp)
                            .background(brush),
                    )
                }
            }
        }
        // 评论卡：用户行 + 正文 + 「查看全部」按钮占位；下方按剩余高度继续补评论卡
        CommentCardSkeleton(brush)
        repeat(extraCommentCards) { CommentCardSkeleton(brush) }
        }
    }
}

/** 详情页评论区占位卡（与真实评论卡同结构：用户行 + 两行正文 + 按钮条）。 */
@Composable
private fun CommentCardSkeleton(brush: Brush) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(84.dp).height(12.dp).squircleClip(6.dp).background(brush))
                Box(Modifier.width(48.dp).height(12.dp).squircleClip(6.dp).background(brush))
            }
            Box(Modifier.fillMaxWidth(0.95f).height(12.dp).squircleClip(6.dp).background(brush))
            Box(Modifier.fillMaxWidth(0.7f).height(12.dp).squircleClip(6.dp).background(brush))
            Box(Modifier.fillMaxWidth().height(44.dp).squircleClip(22.dp).background(brush))
        }
    }
}
