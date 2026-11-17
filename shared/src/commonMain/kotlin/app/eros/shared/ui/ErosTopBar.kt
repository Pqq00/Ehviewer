// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import app.eros.shared.settings.AppSettings
import app.eros.shared.ui.design.GlassRailChrome
import app.eros.shared.ui.effect.erosGlassBlur
import app.eros.shared.ui.effect.isShaderSupported
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    if (!AppSettings.barBlurEnabled || !isShaderSupported()) return null
    val surface = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
}

/**
 * 顶栏图标按钮：模糊可用时用 miuix `GlassIconButton`（OS4 圆形玻璃，自动继承所在
 * `GlassTopAppBar` 的 backdrop 与材质、以及按下时的缩放反馈），否则退回普通 `IconButton`。
 *
 * 只在 [ErosTopBar] 的 `navigationIcon` / `actions` 槽里使用。普通页面里的
 * `SmallTopAppBar`（过滤规则、网络日志、关注标签）保持裸图标，不在本函数覆盖范围。
 */
@Composable
fun ErosBarIcon(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (enabled && AppSettings.barBlurEnabled && isShaderSupported()) {
        GlassIconButton(onClick = onClick, content = content)
    } else {
        IconButton(onClick = onClick, enabled = enabled) { content() }
    }
}

/**
 * 全应用统一的顶栏，基于 miuix `GlassTopAppBar`（OS4 玻璃材质）。
 *
 * 三种状态：
 *  - 渐进模糊（`BarBlurStyle.PROGRESSIVE` + backdrop 可用）：交给 `GlassTopAppBar` 的
 *    `progressiveBlur`，它自己合成「页面内容 + 渐变模糊带」，滚动时浮出。
 *  - 高斯模糊（`BarBlurStyle.GAUSSIAN` + backdrop 可用）：外层套整块 `erosGlassBlur`，
 *    并关掉 `GlassTopAppBar` 自带的渐变带（`backgroundAlpha = { 0f }`），避免双份底色。
 *  - 纯色（backdrop 为 null）：同一个 `GlassTopAppBar`，只铺主题 surface 底色 + 分隔线。
 *
 * 标题折叠、大标题、滚动联动仍由 `GlassTopAppBar` 内部的 `BlurTopAppBar` 负责。
 */
@Composable
fun ErosTopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    navigationIcon: @Composable () -> Unit = {},
    blurStyleOverride: BarBlurStyle? = null,
    bottomContent: @Composable () -> Unit = {},
) {
    val blurStyle = blurStyleOverride ?: currentBarBlurStyle()
    // 宽屏玻璃侧栏：官方 `AdaptiveTopAppBar(expandable = !isWideScreen)` —— 宽屏不放大标题；
    // 侧栏收成顶部胶囊时胶囊会盖住标题，所以折叠态直接把标题置空。
    val railMode = GlassRailChrome.railMode
    val railExpanded = GlassRailChrome.expanded
    val effectiveTitle = if (railMode && !railExpanded) "" else title
    val useBlur = backdrop != null
    val progressive = useBlur && blurStyle == BarBlurStyle.PROGRESSIVE

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                when {
                    progressive -> Modifier
                    useBlur -> Modifier.erosGlassBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = 25f,
                        colors = barBlurColors(),
                    )
                    else -> Modifier.background(MiuixTheme.colorScheme.surface)
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        GlassTopAppBar(
            title = effectiveTitle,
            expandable = !railMode,
            progressiveBlur = progressive,
            backdrop = backdrop,
            scrollBehavior = scrollBehavior,
            // 前景（标题/图标）可以内缩，背景渐变带仍满宽 —— 官方 `contentModifier` 语义：
            // "Modifier for the foreground bar; the background band remains stationary."
            contentModifier = contentModifier,
            // 高斯模糊/纯色时底色由外层负责，关掉自带渐变带。
            backgroundAlpha = { if (progressive) 1f else 0f },
            navigationIcon = navigationIcon,
            actions = actions,
            // 官方示例（example/shared/.../IconPage.kt:222-234）把玻璃分段 Tab 放在顶栏的
            // bottomContent 里：它属于顶栏、不在内容层里，滚起来跟着顶栏走，也不会采到自己。
            bottomContent = bottomContent,
        )
        if (!useBlur) {
            Box(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider()
            }
        }
    }
}
