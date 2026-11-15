// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import app.eros.shared.data.GalleryTitles
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eros.shared.data.DetailSeedStore
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.ThumbUrl
import app.eros.shared.data.createEhApi
import app.eros.shared.settings.AppSettings
import app.eros.shared.system.showSystemToast
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.util.Locale

// 供「与下载卡对齐」的其它列表复用（例如浏览历史），保证封面尺寸/圆角/淡入一致。
internal val CARD_HEIGHT = 160.dp
internal val COVER_WIDTH = 120.dp
internal val COVER_CORNER = 10.dp

private val TITLE_HEIGHT = 55.dp

// 标签芯片：高度显式给定，不再由字体行高撑开，三行总和与 TAGS_HEIGHT 严格相等。
// 3 * 18 + 2 * 2 = 58，正好等于标签区高度，既不会裁掉第三行，也没有空当。
private val TAG_CHIP_HEIGHT = 18.dp
private val TAG_ROW_GAP = 2.dp
private val TAG_COL_GAP = 5.dp
private const val TAG_ROWS = 3
private val TAGS_HEIGHT = TAG_CHIP_HEIGHT * TAG_ROWS + TAG_ROW_GAP * (TAG_ROWS - 1)

/** 单个标签芯片：固定高度，文字垂直居中。
 *
 * 用中性灰而不是品牌色：卡片里标题是最深的黑，标签应当比标题淡一档，
 * 和下面的分类徽标、时间/评分形成「标题 → 标签 → 次要信息」的层次，
 * 不再让蓝色标签抢走整张卡的视觉重心（深浅主题都靠 onSurface 的透明度自适应）。
 *
 * 例外是「汉语」：它决定这本能不能直接看，值得单独标出来，所以用很淡的主色
 * （底 8% 主色、字 90% 主色），比标题淡、比其它标签显眼一点，但不会抢眼。
 */
@Composable
private fun TagChip(text: String, highlighted: Boolean = false) {
    val accent = MiuixTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .height(TAG_CHIP_HEIGHT)
            .squircleSurface(
                color = if (highlighted) accent.copy(alpha = 0.08f)
                else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                cornerRadius = 7.dp,
            )
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 11.sp, lineHeight = 13.sp),
            color = if (highlighted) accent.copy(alpha = 0.90f)
            else MiuixTheme.colorScheme.onSurfaceSecondary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 行优先的流式布局：按 [wrapWidth] 折行，最多 [TAG_ROWS] 行；一屏放不下就整体向右延伸，
 * 宽度会超出容器，交给外层的 horizontalScroll 去滑。
 *
 * 自己测量而不用 FlowRow，是因为 FlowRow 只有在受限宽度下才会折行，而 horizontalScroll
 * 给子节点的宽度约束是无限的。
 */
@Composable
private fun TagChipsFlow(
    wrapWidth: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, _ ->
        val wrapPx = wrapWidth.roundToPx()
        val colGapPx = TAG_COL_GAP.roundToPx()
        val rowGapPx = TAG_ROW_GAP.roundToPx()
        val chipPx = TAG_CHIP_HEIGHT.roundToPx()
        val rowStride = chipPx + rowGapPx

        // 芯片是单行文本，先用无界约束量出各自的自然宽度，再决定怎么排。
        val placeables = measurables.map { it.measure(Constraints()) }
        val positions = ArrayList<IntOffset>(placeables.size)
        var screen = 0
        var row = 0
        var x = 0
        var rightmost = 0
        placeables.forEach { placeable ->
            if (x > 0 && x + placeable.width > wrapPx) {
                x = 0
                row++
            }
            if (row >= TAG_ROWS) {
                // 三行已满，内容接在当前屏右侧，下一屏从新的左边界重新开始。
                screen++
                row = 0
            }
            val left = screen * wrapPx + x
            positions += IntOffset(left, row * rowStride)
            x += placeable.width + colGapPx
            rightmost = maxOf(rightmost, left + placeable.width)
        }

        layout(width = maxOf(rightmost, 1), height = TAGS_HEIGHT.roundToPx()) {
            placeables.forEachIndexed { index, placeable ->
                val offset = positions[index]
                placeable.place(offset.x, offset.y)
            }
        }
    }
}

/**
 * 下载卡的封面区域：squircle 圆角 + 模糊 + 220ms 淡入，未就位时盖一层骨架。
 *
 * 抽出来是为了让浏览历史这类「不在信息流里但要对齐下载卡样式」的列表复用同一套
 * 尺寸与过渡，而不是各自再写一份细节略有出入的封面。
 */
@Composable
internal fun DownloadCardCover(
    thumb: String?,
    modifier: Modifier = Modifier,
    scrolling: Boolean = false,
) {
    val context = LocalPlatformContext.current
    Box(
        modifier = modifier
            .squircleClip(cornerRadius = COVER_CORNER)
            .background(MiuixTheme.colorScheme.surfaceVariant),
    ) {
        var coverReady by remember(thumb) { mutableStateOf(false) }
        var coverFailed by remember(thumb) { mutableStateOf(false) }
        // 封面淡入：图片解码完成的那一刻不要再"啪"地出现，而是 220ms 淡上来，
        // 骨架层同步淡出，两者交叉，中间不会闪出一块底色。
        val coverShown = coverReady || thumb == null || coverFailed
        val coverAlpha by animateFloatAsState(
            targetValue = if (coverShown) 1f else 0f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "coverReveal",
        )
        thumb?.let { t ->
            var useRaw by remember(t) { mutableStateOf(false) }
            val url = remember(t, useRaw) {
                if (useRaw) t else ThumbUrl.forCard(t)
            }
            val request = remember(url) {
                ImageRequest.Builder(context).data(url).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { coverReady = true },
                onError = {
                    if (!useRaw) useRaw = true else coverFailed = true
                },
                placeholder = ColorPainter(MiuixTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxSize()
                    .coverBlur()
                    .graphicsLayer { alpha = coverAlpha },
            )
        }
        // 封面没就位就盖一层骨架：没有缩略图地址（thumb 为空）或两次尝试都失败时不盖，
        // 避免永远闪烁。
        if (coverAlpha < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - coverAlpha }
                    .background(rememberSkeletonBrush(animated = !scrolling)),
            )
        }
    }
}

/**
 * 下载卡样式的信息流卡片：一行一张，左缩略图 + 右侧标题 / 三行标签 / 两行信息。
 * 固定高度保证列表内所有卡片等高。
 */
@Composable
internal fun GalleryCardDownloadStyle(
    gallery: GalleryUi,
    /** 在列表里的位置，用于入场错峰（与经典卡同一套动画）。 */
    index: Int = 0,
    scrolling: Boolean = false,
    onClick: () -> Unit = {},
) {
    var showSheet by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    // 下载卡没有整卡骨架，改为按区域占位：封面/标题/标签/评分各自画到就位为止。
    val entrance = rememberCardEntrance(gallery.gid, index)
    // 标题还没解析出来 = 这一条还不能用：不给点击、不给按压反馈。
    val canOpen = gallery.title.isNotBlank()
    // 标签为空即代表这条还没被 gdata 补齐；此时标签/评分/时间一律画骨架，不显示 0/—。
    val metaReady = gallery.tags.isNotEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .cardEntrance(entrance.progress),
        insideMargin = PaddingValues(8.dp),
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CARD_HEIGHT),
        ) {
            // 左：缩略图（图还没到之前画骨架，避免先出现一块空白）
            DownloadCardCover(
                thumb = gallery.thumb,
                scrolling = scrolling,
                modifier = Modifier
                    .width(COVER_WIDTH)
                    .fillMaxHeight(),
            )

            Spacer(Modifier.width(12.dp))

            // 右：标题 / 标签 / 信息
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                RegionCrossfade(
                    revealed = canOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TITLE_HEIGHT),
                    placeholder = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            SkeletonBar(
                                height = 13.dp,
                                modifier = Modifier.fillMaxWidth(0.95f),
                                animated = !scrolling,
                            )
                            SkeletonBar(
                                height = 13.dp,
                                modifier = Modifier.fillMaxWidth(0.72f),
                                animated = !scrolling,
                            )
                        }
                    },
                    content = {
                        Text(
                            text = gallery.title,
                            style = MiuixTheme.textStyles.subtitle.copy(
                                fontSize = 14.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )

                val tags = gallery.tags
                val tagRevision = app.eros.shared.tags.TagDatabase.revision
                // 标签区高度恒定：补齐前画 3 行 chip 骨架，补齐后同一位置交叉淡入真实 chips。
                RegionCrossfade(
                    revealed = tags.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TAGS_HEIGHT)
                        // 兜底：万一系统字体被放大，宁可裁在标签区边界，也不渗到下面的信息行。
                        .clipToBounds(),
                    placeholder = {
                        SkeletonChips(
                            rows = TAG_ROWS,
                            chipHeight = TAG_CHIP_HEIGHT,
                            rowGap = TAG_ROW_GAP,
                            colGap = TAG_COL_GAP,
                        )
                    },
                    content = {
                        // 行优先流式排布：芯片紧挨着铺，一行放不下就折行，三行放不下整体向右延伸，
                        // 由 horizontalScroll 提供滑动。不能直接用 FlowRow —— 它在 horizontalScroll
                        // 里拿到的是无限宽约束，永远不折行，也就退化成单行。
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val wrapWidth = maxWidth
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                TagChipsFlow(wrapWidth = wrapWidth) {
                                    tags.forEach { tag ->
                                        val translation = remember(tag, AppSettings.tagTranslation, tagRevision) {
                                            if (AppSettings.tagTranslation) {
                                                com.hippo.ehviewer.client.EhTagDatabase.translate(tag)
                                            } else {
                                                null
                                            }
                                        }
                                        // 只有「汉语」这一个标签上淡主色：它决定这本能不能直接读。
                                        TagChip(
                                            text = translation ?: tag.substringAfter(':', tag),
                                            highlighted = tag.equals("language:chinese", ignoreCase = true),
                                        )
                                    }
                                }
                            }
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    categoryName(gallery.category)?.let {
                        Badge(
                            text = it,
                            containerColor = categoryColor(gallery.category),
                            modifier = Modifier.offset(x = (-6).dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    RegionCrossfade(
                        revealed = gallery.pages > 0,
                        durationMs = 160,
                        placeholder = {
                            SkeletonBar(height = 11.dp, modifier = Modifier.width(36.dp), animated = !scrolling)
                        },
                        content = {
                            Text(
                                text = "${gallery.pages} 页",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                maxLines = 1,
                            )
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RegionCrossfade(
                        revealed = metaReady,
                        durationMs = 160,
                        placeholder = {
                            // 评分未补齐：整段画骨架，不再显示“—”这种既像空数据又像失败的内容。
                            SkeletonBar(height = 11.dp, modifier = Modifier.width(72.dp), animated = !scrolling)
                        },
                        content = {
                            if (gallery.rating > 0f) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RatingStars(
                                        rating = gallery.rating,
                                        tint = Color(0xFFF6A821),
                                        starSize = 10.dp,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = String.format(Locale.US, "%.2f", gallery.rating),
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                        maxLines = 1,
                                    )
                                }
                            } else {
                                Text(
                                    text = "—",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                )
                            }
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    RegionCrossfade(
                        revealed = metaReady,
                        durationMs = 160,
                        placeholder = {
                            SkeletonBar(height = 11.dp, modifier = Modifier.width(64.dp), animated = !scrolling)
                        },
                        content = {
                            Text(
                                text = gallery.posted ?: "—",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }

    TrackOverlay(showSheet)
    OverlayBottomSheet(
        show = showSheet,
        title = GalleryTitles.primary(gallery.title, gallery.titleJpn),
        onDismissRequest = { showSheet = false },
        onDismissFinished = { },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "加入收藏夹 0",
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {
                        showSheet = false
                        scope.launch {
                            val ok = runCatching {
                                withContext(Dispatchers.IO) {
                                    createEhApi().setFavourite(gallery.gid, gallery.token, 0)
                                }
                            }.getOrDefault(false)
                            showSystemToast(if (ok) "已加入收藏夹 0" else "收藏失败，请确认已登录")
                        }
                    }, onLongClick = null)
                    .padding(vertical = 14.dp),
            )
            Text(
                text = "复制标题",
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {
                        clipboard.setText(AnnotatedString(GalleryTitles.primary(gallery.title, gallery.titleJpn)))
                        showSheet = false
                        showSystemToast("标题已复制")
                    }, onLongClick = null)
                    .padding(vertical = 14.dp),
            )
            Text(
                text = "复制链接",
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {
                        clipboard.setText(AnnotatedString("https://e-hentai.org/g/${gallery.gid}/"))
                        showSheet = false
                        showSystemToast("链接已复制")
                    }, onLongClick = null)
                    .padding(vertical = 14.dp),
            )
        }
    }
}
