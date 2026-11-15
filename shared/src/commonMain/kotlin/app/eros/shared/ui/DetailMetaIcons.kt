// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Translate

/**
 * 详情页元信息用的小图标。
 *
 * 语言 / 收藏数 / 页数 / 体积 / 日期直接用 miuix 扩展图标，与 App 其它地方的线性风格一致；
 * 评分在 miuix-icons 0.9.4 的 156 个扩展图标里没有对应图形（无 star），仍用 Canvas 画，
 * 且与下方 [RatingStars] 共用同一套画法。
 */
internal enum class MetaIconKind { Globe, Disk, Star, Heart, Doc, Clock }

@Composable
internal fun MetaIcon(
    kind: MetaIconKind,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
) {
    val vector = when (kind) {
        MetaIconKind.Globe -> MiuixIcons.Translate
        MetaIconKind.Heart -> MiuixIcons.Favorites
        MetaIconKind.Doc -> MiuixIcons.Notes
        MetaIconKind.Disk -> MiuixIcons.Folder
        MetaIconKind.Clock, MetaIconKind.Star -> null
    }
    if (vector != null) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            modifier = modifier.size(size),
            tint = tint,
        )
        return
    }
    Canvas(modifier = modifier.then(Modifier.size(size))) {
        when (kind) {
            MetaIconKind.Clock -> drawClock(tint)
            MetaIconKind.Star -> drawStar(tint, 1f)
            else -> Unit
        }
    }
}

/** 5 星评分：按小数部分裁切最后一颗星。 */
@Composable
internal fun RatingStars(
    rating: Float,
    tint: Color,
    modifier: Modifier = Modifier,
    starSize: Dp = 15.dp,
) {
    Canvas(modifier = modifier.then(Modifier.size(width = starSize * 5 + 8.dp, height = starSize))) {
        val gap = 2.dp.toPx()
        val cell = (size.width - gap * 4) / 5f
        for (i in 0 until 5) {
            val left = i * (cell + gap)
            val fraction = (rating - i).coerceIn(0f, 1f)
            clipRect(left = left, top = 0f, right = left + cell, bottom = size.height) {
                drawStarPath(tint.copy(alpha = 0.22f), left, 0f, cell, size.height)
                if (fraction > 0f) {
                    clipRect(left = left, top = 0f, right = left + cell * fraction, bottom = size.height) {
                        drawStarPath(tint, left, 0f, cell, size.height)
                    }
                }
            }
        }
    }
}

/** 线宽与 miuix 图标（24dp viewport、2dp 描边）在 14~15dp 下的观感对齐。 */
private fun DrawScope.strokeWidth() = 1.2.dp.toPx()

/** 日期：miuix 没有纯线框时钟（Alarm 带铃、Recent 是实心盘），这里继续用 Canvas 画。 */
private fun DrawScope.drawClock(color: Color) {
    val sw = strokeWidth()
    val r = size.minDimension / 2f - sw
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(color, r, c, style = Stroke(sw))
    drawLine(color, c, Offset(c.x, c.y - r * 0.55f), sw)
    drawLine(color, c, Offset(c.x + r * 0.42f, c.y + r * 0.2f), sw)
}

private fun DrawScope.drawStar(color: Color, fraction: Float) {
    drawStarPath(color, 0f, 0f, size.width, size.height)
}

private fun DrawScope.drawStarPath(color: Color, left: Float, top: Float, w: Float, h: Float) {
    val cx = left + w / 2f
    val cy = top + h / 2f
    val outer = minOf(w, h) / 2f - 0.5.dp.toPx()
    val inner = outer * 0.45f
    val path = Path()
    for (i in 0 until 10) {
        val angle = Math.toRadians((-90 + i * 36).toDouble())
        val radius = if (i % 2 == 0) outer else inner
        val x = (cx + radius * kotlin.math.cos(angle)).toFloat()
        val y = (cy + radius * kotlin.math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}
