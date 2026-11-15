// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eros.shared.reader.PageImage
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 单页占位状态：250ms 内加载完就不闪圈，200ms 淡出；
 * 失败页内联「点按重试」，地址未取到时显示「获取地址…」。
 *
 * [foreground] 是当前页面底色对应的前景色（浅色主题白底用黑、深色/沉浸用白），
 * 直接来自阅读器的 `pageTextColor`，与底色一起做 220ms 过渡。
 */
@Composable
internal fun PageStatus(
    img: PageImage,
    onRetry: () -> Unit,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    val kind = when (img) {
        is PageImage.Ready -> "ready"
        is PageImage.Failed -> "failed"
        is PageImage.Loading -> "loading"
        else -> "address"
    }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(kind) {
        when (kind) {
            "ready" -> show = false
            "failed" -> show = true
            else -> {
                delay(250)
                show = true
            }
        }
    }
    AnimatedVisibility(
        visible = show && kind != "ready",
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(200)),
        modifier = modifier,
    ) {
        when (img) {
            is PageImage.Loading -> PageRing(progress = img.progress, foreground = foreground)
            is PageImage.Failed -> FailedBlock(
                message = img.message,
                onRetry = onRetry,
                foreground = foreground,
            )
            else -> PageAddress(foreground = foreground)
        }
    }
}

/** 页面中心的小圆环：前景色描边 + 百分比；地址未取到时用不确定态。 */
@Composable
internal fun PageRing(progress: Float, foreground: Color, indeterminate: Boolean = false) {
    val p = progress.coerceIn(0f, 1f)
    val transition = rememberInfiniteTransition(label = "pageRing")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "pageRingSpin",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(40.dp)) {
            val stroke = 2.5.dp.toPx()
            val inset = stroke / 2f
            drawCircle(
                color = foreground.copy(alpha = 0.18f),
                radius = size.minDimension / 2f - inset,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = foreground,
                startAngle = if (indeterminate) spin - 90f else -90f,
                sweepAngle = if (indeterminate) 96f else 360f * p,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        if (!indeterminate && p > 0f) {
            Text(
                "${(p * 100).toInt()}%",
                style = MiuixTheme.textStyles.footnote2.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = foreground.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 地址还没取到时的提示。 */
@Composable
internal fun PageAddress(foreground: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PageRing(progress = 0f, foreground = foreground, indeterminate = true)
        Text(
            "获取地址…",
            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 13.sp),
            color = foreground.copy(alpha = 0.72f),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** 加载失败的整块提示（带重试）。 */
@Composable
internal fun FailedBlock(
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    foreground: Color = Color.White,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "加载失败",
            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = foreground,
        )
        if (!message.isNullOrBlank()) {
            Text(
                message,
                style = MiuixTheme.textStyles.footnote2.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = foreground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (onRetry != null) {
            Text(
                "点按重试",
                style = MiuixTheme.textStyles.footnote2.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { onRetry() },
            )
        }
    }
}
