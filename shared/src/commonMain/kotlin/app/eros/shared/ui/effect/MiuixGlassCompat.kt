// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.effect

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.material.RuntimeShader
import top.yukonga.miuix.kmp.material.asBrush
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

/*
 * 全仓与 miuix 玻璃/着色器 API 的唯一直连点。
 *
 * 当前依赖 miuix main 的快照（0.9.4-3a980f7c-SNAPSHOT，见 settings.gradle.kts）。
 * 相对 0.9.4 的变化：
 *   1. RuntimeShader / asBrush / isRuntimeShaderSupported 从 blur 移到 material；
 *   2. 删除 blur.highlight.*（Highlight / HighlightStyle），textureBlur 与
 *      progressiveTextureBlur 的 highlight 参数一并移除，边光改为 material 的
 *      Modifier.edgeStroke(...) / EdgeStroke；
 *   3. miuix-shader 模块被并入 miuix-material。
 *
 * 所以界面代码只依赖本文件：以后 miuix 再挪包，改这里即可。
 */

/** 是否支持 RuntimeShader（Android 13+ 才有真实效果，低版本自动跳过）。 */
fun isShaderSupported(): Boolean = isRuntimeShaderSupported()

/**
 * 玻璃模糊。
 *
 * 0.9.4 时这里还接受 `highlight` 参数描边光；新版 miuix 把边光独立成
 * `material.Modifier.edgeStroke(...)`，不在模糊里画，需要时另行叠加。
 */
fun Modifier.erosGlassBlur(
    backdrop: Backdrop,
    shape: Shape,
    blurRadius: Float,
    colors: BlurColors,
): Modifier = textureBlur(
    backdrop = backdrop,
    shape = shape,
    blurRadius = blurRadius,
    colors = colors,
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
