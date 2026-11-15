// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 卡片入场进度：1 = 完全就位，0 = 未开始。 */
internal class CardEntrance(val progress: Float, val skeletonAlpha: Float)

/**
 * 卡片入场动画，两种卡片样式共用，保证观感一致。
 *
 * 0 → 1：透明淡入 + 0.90 → 1 缩放 + 自下方 14dp 上滑，spring 收尾；
 * 同屏最多错开 5 个位置（0~175ms），每个 gid 只播一次（滑走再回来不重播）。
 *
 * @param revealed 封面是否已有内容可显示（经典卡等封面就绪再入场，下载卡一直为 true）。
 */
@Composable
internal fun rememberCardEntrance(
    gid: Long,
    index: Int,
    revealed: Boolean = true,
): CardEntrance {
    var played by rememberSaveable(gid) { mutableStateOf(false) }
    var entered by remember(gid) { mutableStateOf(false) }
    LaunchedEffect(revealed, played) {
        if (!revealed || entered) return@LaunchedEffect
        if (!played) {
            delay((index % 6) * 35L)
            played = true
        }
        entered = true
    }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "cardEntrance",
    )
    val skeletonAlpha by animateFloatAsState(
        targetValue = if (entered) 0f else 1f,
        animationSpec = tween(120),
        label = "cardSkeletonAlpha",
    )
    return CardEntrance(progress, skeletonAlpha)
}

/** 把入场进度套到卡片上（淡入 + 缩放 + 上滑）。 */
internal fun Modifier.cardEntrance(progress: Float): Modifier = graphicsLayer {
    alpha = progress
    val scale = 0.90f + 0.10f * progress
    scaleX = scale
    scaleY = scale
    translationY = (1f - progress) * 14.dp.toPx()
}

/** 封面上的小角标（半透明底 + 白字）。 */
@Composable
internal fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Black.copy(alpha = 0.45f),
    outerPadding: Dp = 6.dp,
) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .padding(outerPadding)
            .squircleSurface(color = containerColor, cornerRadius = 6.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * 卡片级骨架：只占封面区域，高度与真实封面一致。
 * 标题区由真实内容（alpha=0）撑开，所以淡入时不会跳高度。
 */
@Composable
internal fun CardSkeleton(
    aspect: Float,
    modifier: Modifier = Modifier,
) {
    val base = MiuixTheme.colorScheme.surfaceVariant
    val highlight = MiuixTheme.colorScheme.surfaceContainerHigh
    val alpha by rememberInfiniteTransition(label = "cardSkeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cardSkeletonAlpha",
    )
    val brush = Brush.linearGradient(
        listOf(
            base.copy(alpha = alpha),
            highlight.copy(alpha = alpha * 0.6f),
            base.copy(alpha = alpha),
        ),
    )
    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .squircleClip(cornerRadius = 10.dp)
                .background(brush),
        )
    }
}

/**
 * 卡片内“信息未补齐”的占位渐变，节奏与 [CardSkeleton] 完全一致。
 *
 * @param animated 列表滑动中传 false：只画静态灰块，不跑无限动画（省电、稳帧）。
 */
@Composable
internal fun rememberSkeletonBrush(animated: Boolean = true): Brush {
    val base = MiuixTheme.colorScheme.surfaceVariant
    val highlight = MiuixTheme.colorScheme.surfaceContainerHigh
    val animatedAlpha = if (animated) {
        val alpha by rememberInfiniteTransition(label = "cardBarSkeleton").animateFloat(
            initialValue = 0.35f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "cardBarSkeletonAlpha",
        )
        alpha
    } else {
        0.55f
    }
    return Brush.linearGradient(
        listOf(
            base.copy(alpha = animatedAlpha),
            highlight.copy(alpha = animatedAlpha * 0.6f),
            base.copy(alpha = animatedAlpha),
        ),
    )
}

/**
 * 一条骨架条：宽度交给调用方的 [modifier] 决定（定宽或按比例）。
 *
 * 只画占位、不进无障碍树，避免 TalkBack 把占位当成内容念出来。
 */
@Composable
internal fun SkeletonBar(
    height: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = height * 0.5f,
    animated: Boolean = true,
) {
    val brush = rememberSkeletonBrush(animated)
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(brush)
            .clearAndSetSemantics { },
    )
}

/**
 * 标签区占位：固定 [rows] 行 chip 形状的骨架。
 *
 * 行高、行距、列距与真实 chip 一致，所以补齐后不会改变卡片高度。
 * 底色也刻意和真实 chip 用同一个中性灰（`onSurface` 6%），
 * 这样「骨架 → 真标签」只是灰色块里长出文字，不会出现灰块变蓝块那一下。
 * 因此这里不跑 shimmer：标签区是一排小色块，跑动画反而比别的占位更吵。
 */
@Composable
internal fun SkeletonChips(
    modifier: Modifier = Modifier,
    rows: Int = 3,
    chipHeight: Dp = 18.dp,
    rowGap: Dp = 2.dp,
    colGap: Dp = 5.dp,
) {
    val widths = listOf(
        listOf(72.dp, 96.dp, 56.dp, 88.dp),
        listOf(56.dp, 80.dp, 104.dp, 64.dp),
        listOf(88.dp, 64.dp, 72.dp, 60.dp),
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(rowGap)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(colGap)) {
                widths[row % widths.size].forEach { width ->
                    Box(
                        modifier = Modifier
                            .width(width)
                            .height(chipHeight)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}

/**
 * 单块区域的「骨架 → 内容」淡入进度（0 = 全骨架，1 = 全内容）。
 *
 * 首帧就已经是补齐状态时直接返回 1，不会让完整内容从透明里淡进来。
 */
@Composable
internal fun rememberRegionReveal(revealed: Boolean, durationMs: Int = 180): Float {
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
        label = "regionReveal",
    )
    return alpha
}

/**
 * 区域级交叉淡入：骨架层与内容层叠在同一个 [Box] 里，alpha 互补，
 * 位置与尺寸都不参与布局变化，所以补齐的瞬间不会跳。
 *
 * 用于卡片内部的标题、标签区、页数、评分、发布时间 —— 这些字段是分别到达的，
 * 逐块交叉淡入比整卡替换自然。
 */
@Composable
internal fun RegionCrossfade(
    revealed: Boolean,
    modifier: Modifier = Modifier,
    durationMs: Int = 180,
    placeholder: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val alpha = rememberRegionReveal(revealed, durationMs)
    Box(modifier) {
        if (alpha < 1f) {
            Box(Modifier.graphicsLayer { this.alpha = 1f - alpha }) { placeholder() }
        }
        if (alpha > 0f) {
            Box(Modifier.graphicsLayer { this.alpha = alpha }) { content() }
        }
    }
}

/**
 * 整页骨架层的 alpha：出现是瞬时的（加载开始就该有骨架，不能淡入 —— 快网络下还没淡完
 * 数据就到了，等于没有骨架），消失是 [durationMs] 淡出。
 *
 * 页级交叉没法像卡片内部那样在同一个 Box 里叠两层（骨架是网格项，真内容是另一批网格项），
 * 所以骨架作为一层覆盖在真实列表上方淡出；两者同时存在，用户看到的是「骨架化成卡片」。
 */
@Composable
internal fun rememberSkeletonOverlayAlpha(visible: Boolean, durationMs: Int = 200): Float {
    val alpha = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        if (visible) {
            alpha.snapTo(1f)
        } else if (alpha.value > 0f) {
            alpha.animateTo(0f, animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing))
        }
    }
    return alpha.value
}
