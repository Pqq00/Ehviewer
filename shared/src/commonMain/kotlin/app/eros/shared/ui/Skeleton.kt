// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eros.shared.settings.AppSettings
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首屏骨架卡片：与 [GalleryCard] 尺寸一致，用呼吸式渐变代替居中转圈。
 * 感知上比 spinner 快，也避免"白屏 → 突然出现一堆卡片"的跳变。
 */
@Composable
fun SkeletonCard() {
    if (AppSettings.cardStyle == "download") {
        DownloadStyleSkeleton()
        return
    }
    val base = MiuixTheme.colorScheme.surfaceVariant
    val highlight = MiuixTheme.colorScheme.surfaceContainerHigh
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val brush = Brush.linearGradient(
        listOf(
            base.copy(alpha = alpha),
            highlight.copy(alpha = alpha * 0.6f),
            base.copy(alpha = alpha),
        ),
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .squircleClip(cornerRadius = 10.dp)
                    .background(brush),
            )
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.85f)
                    .height(12.dp)
                    .squircleClip(cornerRadius = 6.dp)
                    .background(base.copy(alpha = alpha * 0.8f)),
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(0.55f)
                    .height(12.dp)
                    .squircleClip(cornerRadius = 6.dp)
                    .background(base.copy(alpha = alpha * 0.8f)),
            )
        }
    }
}


/** 下载卡样式的骨架：一行一张、176dp 高，与真实卡片同高。 */
@Composable
private fun DownloadStyleSkeleton() {
    val base = MiuixTheme.colorScheme.surfaceVariant
    val highlight = MiuixTheme.colorScheme.surfaceContainerHigh
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val brush = Brush.linearGradient(
        listOf(
            base.copy(alpha = alpha),
            highlight.copy(alpha = alpha * 0.6f),
            base.copy(alpha = alpha),
        ),
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .squircleClip(cornerRadius = 10.dp)
                    .background(brush),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.fillMaxHeight()) {
                Box(Modifier.fillMaxWidth().height(18.dp).background(brush))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.7f).height(18.dp).background(brush))
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth(0.45f).height(14.dp).background(brush))
            }
        }
    }
}


/**
 * 整页骨架覆盖层：与真实网格同列数、同间距、同内容内边距，叠在列表上方。
 *
 * 出现是瞬时的（加载开始就该有骨架，不能淡入 —— 否则快网络下还没淡完数据就到了，等于没有骨架），
 * 消失是 200ms 淡出（[rememberSkeletonOverlayAlpha]）。`header` 用来画那些骨架期间就该显示的
 * 部分（分类 chips / 周期 chips），这样首屏的上半部分从第一帧起就是真的，只有卡片区域在呼吸。
 *
 * 只画不滚：`userScrollEnabled = false`，它只在首屏加载期存在。
 */
@Composable
fun SkeletonGridOverlay(
    visible: Boolean,
    columns: Int,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 12.dp,
    header: (@Composable () -> Unit)? = null,
) {
    val alpha = rememberSkeletonOverlayAlpha(visible)
    if (alpha <= 0.01f) return
    // 条数按可用空间算：手机 6 张刚好一屏多，平板/横屏列多行少，写死 6 张就只剩一行半。
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
    ) {
        val layoutDirection = LocalLayoutDirection.current
        val sidePadding = contentPadding.calculateLeftPadding(layoutDirection) +
            contentPadding.calculateRightPadding(layoutDirection)
        val verticalPadding = contentPadding.calculateTopPadding() +
            contentPadding.calculateBottomPadding()
        // header 高度要等它真画出来才知道，这里多留一行兜住。
        val itemCount = skeletonGridItemCount(
            availableWidth = (maxWidth - sidePadding).coerceAtLeast(1.dp),
            availableHeight = (maxHeight - verticalPadding).coerceAtLeast(1.dp),
            columns = columns,
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
            downloadStyle = AppSettings.cardStyle == "download",
            headerHeight = if (header != null) horizontalSpacing else 0.dp,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            userScrollEnabled = false,
        ) {
            if (header != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "skeletonHeader") { header() }
            }
            items(itemCount) { SkeletonCard() }
        }
    }
}
