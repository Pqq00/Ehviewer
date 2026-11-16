// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.effect

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.RuntimeShader
import top.yukonga.miuix.kmp.blur.asBrush
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.textureBlur

/*
 * 全仓与 miuix 玻璃/着色器 API 的唯一直连点。
 *
 * 当前依赖 0.9.4。miuix main（0.10 起）的改动：
 *   1. RuntimeShader / asBrush / isRuntimeShaderSupported 从 blur 移到 material；
 *   2. 删除 blur.highlight.*（Highlight / HighlightStyle）与 textureBlur 的 highlight 参数，
 *      边光改为 material 的 Modifier.edgeStroke(...) / EdgeStroke；
 *   3. 新增 miuix-material / miuix-glass / miuix-glass-icons 三个发布物。
 *
 * 升级时只改本文件即可，界面代码无需改动。
 */

/** 边光预设。0.9.4：`blur.highlight.Highlight`；0.10 起换成 `material.EdgeStroke`。 */
typealias GlassHighlight = Highlight

/** 是否支持 RuntimeShader（Android 13+ 才有真实效果，低版本自动跳过）。 */
fun isShaderSupported(): Boolean = isRuntimeShaderSupported()

/** 深/浅色各一套中等强度边光。 */
@Composable
fun rememberGlassHighlight(): GlassHighlight {
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        if (dark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
    }
}

/** 玻璃模糊；传 [highlight] 时描一圈边光。 */
fun Modifier.erosGlassBlur(
    backdrop: Backdrop,
    shape: Shape,
    blurRadius: Float,
    colors: BlurColors,
    highlight: GlassHighlight? = null,
): Modifier = textureBlur(
    backdrop = backdrop,
    shape = shape,
    blurRadius = blurRadius,
    colors = colors,
    highlight = highlight,
)

/** 渐变玻璃模糊（顶栏/底栏按方向淡出）。 */
fun Modifier.erosProgressiveGlassBlur(
    backdrop: Backdrop,
    shape: Shape,
    blurRadius: Float,
    colors: BlurColors,
    gradient: ProgressiveBlur,
): Modifier = progressiveTextureBlur(
    backdrop = backdrop,
    shape = shape,
    blurRadius = blurRadius,
    gradient = gradient,
    colors = colors,
)

/** AGSL RuntimeShader 构造。 */
fun glassRuntimeShader(shaderCode: String): RuntimeShader = RuntimeShader(shaderCode)

/** RuntimeShader 转成可用的 Brush。 */
fun RuntimeShader.asGlassBrush() = asBrush()
