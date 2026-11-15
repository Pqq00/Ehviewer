// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.background

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.eros.shared.settings.AppSettings
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

/**
 * 栏体模糊配色：底色按比例混合，保证栏内文字可读（官方 `barBlurColors`）。
 */
@Composable
fun barBlurColors(surfaceAlpha: Float = 0.8f, useSurfaceContainer: Boolean = false): BlurColors {
    val base = if (useSurfaceContainer) MiuixTheme.colorScheme.surfaceContainer
    else MiuixTheme.colorScheme.surface
    return BlurDefaults.blurColors(
        blendColors = listOf(
            BlendColorEntry(color = base.copy(alpha = surfaceAlpha)),
        ),
    )
}

/**
 * 创建用于栏体模糊的 backdrop；不可用时返回 null（调用方自动退回纯色）。
 * 需要在内容容器上挂 `Modifier.layerBackdrop(backdrop)`。
 */
@Composable
fun rememberBarBackdrop(): LayerBackdrop? {
    if (!AppSettings.barBlurEnabled || !isRuntimeShaderSupported()) return null
    val surface = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
}

/**
 * 全应用统一的顶栏：模糊/纯色 + 大标题折叠 + 可选分隔线。
 *
 * 与官方 `BlurredBar + AdaptiveTopAppBar` 对应；各页面在自己的 Scaffold 里用它，
 * 这样切换 tab 时整页（含顶栏）一起滑动。
 */
@Composable
fun ErosTopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    navigationIcon: @Composable () -> Unit = {},
    blurStyleOverride: BarBlurStyle? = null,
) {
    val effectiveBackdrop = backdrop
    val useBlur = effectiveBackdrop != null
    val blurStyle = blurStyleOverride ?: currentBarBlurStyle()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (useBlur) {
                    if (blurStyle == BarBlurStyle.PROGRESSIVE) {
                        Modifier.progressiveTextureBlur(
                            backdrop = effectiveBackdrop,
                            shape = RectangleShape,
                            gradient = ProgressiveBlur.Top.copy(curve = 1.8f),
                            blurRadius = 14f,
                            colors = barBlurColors(0.5f),
                        )
                    } else {
                        Modifier.textureBlur(
                            backdrop = effectiveBackdrop,
                            shape = RectangleShape,
                            blurRadius = 25f,
                            colors = barBlurColors(),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .background(if (useBlur) Color.Transparent else MiuixTheme.colorScheme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        TopAppBar(
            title = title,
            color = Color.Transparent,
            scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon,
            actions = actions,
        )
        if (!useBlur) {
            HorizontalDivider()
        }
    }
}
