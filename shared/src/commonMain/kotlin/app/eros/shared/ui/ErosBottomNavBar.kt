// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import app.eros.shared.settings.AppSettings
import app.eros.shared.ui.effect.erosGlassBlur
import app.eros.shared.ui.effect.glassEdgeStroke
import kotlin.math.max
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.BookmarkOutline
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 底部导航项。 */
data class BottomTab(val title: String, val icon: ImageVector)

/** 应用主导航项（Tabs 页与详情页共用）。 */
val AppTabs = listOf(
    BottomTab("首页", MiuixIcons.Glass.HomeOutline),
    BottomTab("排行榜", MiuixIcons.TopDownloads),
    BottomTab("收藏", MiuixIcons.Glass.BookmarkOutline),
    BottomTab("下载", MiuixIcons.Download),
    BottomTab("设置", MiuixIcons.Settings),
)

/**
 * 应用级常驻底栏：Tabs ↔ Detail 共用同一个实例。
 *
 * 宽度动画与内容淡变走**同一条时间轴**：两个内容（导航项 / 详情操作项）
 * 在同一容器里叠加、按自然宽度测量、居中摆放，容器宽度随进度插值，
 * 因此胶囊背景、阴影、模糊始终与宽度一致，不会出现"竖线裁切"或阴影滞后。
 */
@Composable
fun ErosAppBottomBar(
    isDetail: Boolean,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    detailEnabled: Boolean,
    detailFavorited: Boolean,
    detailFavoriteCount: Int,
    detailReadPage: Int?,
    detailDownloadLabel: String,
    detailDownloadActive: Boolean,
    detailReadStarted: Boolean = false,
    onDetailFavorite: () -> Unit,
    onDetailDownload: () -> Unit,
    onDetailRead: () -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    // 0 = 导航项，1 = 详情操作项
    val progress by animateFloatAsState(
        targetValue = if (isDetail) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "bottomBarProgress",
    )

    // 过渡动画进行中（0 < progress < 1）时屏蔽所有点击，避免误触
    val transitioning = progress > 0f && progress < 1f

    val navContent: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FloatingNavigationBarDefaults.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTabs.forEachIndexed { index, tab ->
                NavigationItemFor(
                    selected = selectedTab == index,
                    onClick = { onSelectTab(index) },
                    icon = tab.icon,
                    label = tab.title,
                    enabled = !isDetail && !transitioning,
                )
            }
        }
    }

    val detailContent: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FloatingNavigationBarDefaults.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavigationItemFor(
                selected = detailFavorited,
                onClick = onDetailFavorite,
                icon = MiuixIcons.Favorites,
                label = "收藏",
                enabled = detailEnabled && isDetail && !transitioning,
                tint = MiuixTheme.colorScheme.primary,
                badgeCount = detailFavoriteCount.takeIf { it > 0 },
            )
            NavigationItemFor(
                selected = detailDownloadActive,
                onClick = onDetailDownload,
                icon = MiuixIcons.Download,
                label = detailDownloadLabel,
                enabled = detailEnabled && isDetail && !transitioning,
                tint = MiuixTheme.colorScheme.primary,
            )
            NavigationItemFor(
                selected = detailReadStarted,
                onClick = onDetailRead,
                icon = MiuixIcons.Play,
                label = "阅读",
                enabled = detailEnabled && isDetail && !transitioning,
                tint = MiuixTheme.colorScheme.primary,
                badgeCount = detailReadPage?.takeIf { it > 0 },
            )
        }
    }

    if (AppSettings.navBarStyle == 2) {
        val barItems = if (isDetail) {
            listOf(
                IosBarItem(
                    icon = MiuixIcons.Favorites,
                    label = "收藏",
                    active = detailFavorited,
                    enabled = detailEnabled && isDetail && !transitioning,
                    badge = detailFavoriteCount.takeIf { it > 0 }?.toString(),
                ),
                IosBarItem(
                    icon = MiuixIcons.Download,
                    label = detailDownloadLabel,
                    active = detailDownloadActive,
                    enabled = detailEnabled && isDetail && !transitioning,
                ),
                IosBarItem(
                    icon = MiuixIcons.Play,
                    label = "阅读",
                    active = detailReadStarted,
                    enabled = detailEnabled && isDetail && !transitioning,
                    badge = detailReadPage?.takeIf { it > 0 }?.toString(),
                ),
            )
        } else {
            AppTabs.mapIndexed { index, tab ->
                IosBarItem(
                    icon = tab.icon,
                    label = tab.title,
                    active = selectedTab == index,
                    enabled = !isDetail && !transitioning,
                )
            }
        }
        ErosIosBottomBar(
            items = barItems,
            selectedIndex = if (isDetail) -1 else selectedTab,
            // 详情态的三项是动作而不是页签：收藏 / 下载 / 阅读，必须按位置分派。
            onSelect = if (isDetail) {
                { index ->
                    when (index) {
                        0 -> onDetailFavorite()
                        1 -> onDetailDownload()
                        else -> onDetailRead()
                    }
                }
            } else {
                onSelectTab
            },
            // 详情态也可以横向拖拽、松手吸附到最近的一项；重复点同一项仍需再次触发动作。
            repeatActivation = isDetail,
            backdrop = backdrop,
            modifier = modifier,
            // 详情态的三项与「悬浮」样式同款，用它的自然宽度对齐胶囊宽度与位置。
            measureContent = if (isDetail) detailContent else null,
        )
    } else if (AppSettings.navBarStyle == 3) {
        // 玻璃胶囊底栏：直接用 miuix 的 GlassNavigationBar（OS4 材质），
        // 详情态换成「收藏 / 下载 / 阅读」三项，指示器跟随当前状态。
        // 底部留白照官方 GlassPage.kt:244-257：安全区 + 8dp，最少 24dp。
        val safeBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
        val capsuleBottomGap = maxOf(24.dp, safeBottom + 8.dp)
        val glassItems: List<GlassNavigationItem>
        val glassSelected: Int
        if (isDetail) {
            glassItems = listOf(
                GlassNavigationItem(
                    icon = MiuixIcons.Favorites,
                    label = "收藏",
                    contentDescription = "收藏",
                    badge = detailFavoriteCount.takeIf { it > 0 }?.let { count ->
                        { Badge { Text(count.toString()) } }
                    },
                ),
                GlassNavigationItem(
                    icon = MiuixIcons.Download,
                    label = detailDownloadLabel,
                    contentDescription = detailDownloadLabel,
                ),
                GlassNavigationItem(
                    icon = MiuixIcons.Play,
                    label = "阅读",
                    contentDescription = "阅读",
                    badge = detailReadPage?.takeIf { it > 0 }?.let { page ->
                        { Badge { Text(page.toString()) } }
                    },
                ),
            )
            glassSelected = when {
                detailFavorited -> 0
                detailDownloadActive -> 1
                detailReadStarted -> 2
                else -> 0
            }
        } else {
            glassItems = AppTabs.map { tab ->
                GlassNavigationItem(
                    icon = tab.icon,
                    label = tab.title,
                    contentDescription = tab.title,
                )
            }
            glassSelected = selectedTab
        }
        // 官方 GlassPage.kt:250-257 用 layout 在胶囊下方补一段安全区留白：
        // 胶囊本身按约束原位摆放，容器高度多出让出的那一段，从而实现「浮在安全区之上」。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val bottom = capsuleBottomGap.roundToPx().coerceIn(0, constraints.maxHeight)
                    val placeable = measurable.measure(constraints.offset(vertical = -bottom))
                    layout(placeable.width, placeable.height + bottom) {
                        placeable.placeRelative(0, 0)
                    }
                },
        ) {
            GlassNavigationBar(
                items = glassItems,
                selectedIndex = glassSelected,
                onSelect = { index ->
                    if (!transitioning) {
                        if (isDetail) {
                            if (detailEnabled) {
                                when (index) {
                                    0 -> onDetailFavorite()
                                    1 -> onDetailDownload()
                                    else -> onDetailRead()
                                }
                            }
                        } else {
                            onSelectTab(index)
                        }
                    }
                },
                backdrop = backdrop,
                // 官方 AppContent.kt:697-700：玻璃底栏是左右各留 24dp 的胶囊；上下留白见外层包裹。
                defaultWindowInsetsPadding = false,
                modifier = modifier.padding(horizontal = 24.dp),
            )
        }
    } else if (AppSettings.navBarStyle == 1) {
        val shape = RoundedCornerShape(top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults.CornerRadius)
        FloatingNavigationBar(
            modifier = modifier.then(
                if (backdrop != null) {
                    Modifier
                        .erosGlassBlur(
                            backdrop = backdrop,
                            shape = shape,
                            blurRadius = 25f,
                            colors = barBlurColors(0.6f, useSurfaceContainer = true),
                        )
                        .edgeStroke(shape = shape, stroke = glassEdgeStroke())
                } else {
                    Modifier
                },
            ),
            color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
        ) {
            AnimatedBarContent(
                progress = progress,
                navContent = navContent,
                detailContent = detailContent,
            )
        }
    } else {
        val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
        val displayMode = when (AppSettings.navBarMode) {
            1 -> NavigationBarDisplayMode.IconOnly
            2 -> NavigationBarDisplayMode.IconWithSelectedLabel
            else -> NavigationBarDisplayMode.IconAndText
        }
        Box(
            modifier = modifier
                .then(
                    if (backdrop != null) {
                        Modifier.erosGlassBlur(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            blurRadius = 25f,
                            colors = barBlurColors(),
                        )
                    } else {
                        Modifier
                    },
                )
                .background(barColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Crossfade(
                targetState = isDetail,
                animationSpec = tween(durationMillis = 180),
                label = "bottomBarFixedContent",
            ) { detail ->
                NavigationBar(color = barColor, mode = displayMode) {
                    if (!detail) {
                        AppTabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { onSelectTab(index) },
                                icon = tab.icon,
                                label = tab.title,
                            )
                        }
                    } else {
                        NavigationBarItem(
                            selected = detailFavorited,
                            onClick = onDetailFavorite,
                            icon = MiuixIcons.Favorites,
                            label = "收藏",
                            enabled = detailEnabled,
                        )
                        NavigationBarItem(
                            selected = detailDownloadActive,
                            onClick = onDetailDownload,
                            icon = MiuixIcons.Download,
                            label = detailDownloadLabel,
                            enabled = detailEnabled,
                        )
                        NavigationBarItem(
                            selected = detailReadStarted,
                            onClick = onDetailRead,
                            icon = MiuixIcons.Play,
                            label = "阅读",
                            enabled = detailEnabled,
                            badge = detailReadPage?.takeIf { it > 0 }?.let { page ->
                                { Badge { Text(page.toString()) } }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 「导航项显示」：仅图标 / 仅选中项显示文字 / 图标+文字，与固定样式的三种模式对齐。
 */
private fun floatingLabelVisible(selected: Boolean): Boolean = when (AppSettings.navBarMode) {
    1 -> false
    2 -> selected
    else -> true
}

/** iOS 胶囊的条目尺寸：图标 22dp、文字 11.sp、图标与文字间距 1dp、条目 52dp 见方。 */
private val IosNavIconSize = 22.dp
private val IosNavItemSize = 52.dp
private val IosNavIconLabelGap = 1.dp

/**
 * 单个导航项：自绘浮动模式的 item。
 *
 * miuix 的 FloatingNavigationBarItem 只绘制图标（label 仅作无障碍描述），
 * 悬浮底栏要支持「图标+文字」只能自己画。尺寸和间距直接对齐 iOS 胶囊
 * （`ErosIosBar` / `LiquidGlassNavigationBar`）：图标 22dp、文字 11.sp、
 * 两者间距 1dp、条目 52dp 见方，颜色沿用 miuix 的选中/按下/禁用规则。
 */
@Composable
private fun NavigationItemFor(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: Color = MiuixTheme.colorScheme.onSurfaceContainer,
    badgeCount: Int? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val showLabel = floatingLabelVisible(selected)
    val unselected = MiuixTheme.colorScheme.onSurfaceContainer
    val contentColor = when {
        !enabled -> unselected.copy(alpha = unselected.alpha * 0.3f)
        selected && pressed -> tint.copy(alpha = tint.alpha * NavigationBarDefaults.SelectedPressedAlpha)
        selected -> tint
        pressed -> unselected.copy(alpha = unselected.alpha * NavigationBarDefaults.UnselectedPressedAlpha)
        else -> unselected.copy(alpha = unselected.alpha * FloatingNavigationBarDefaults.UnselectedAlpha)
    }
    val iconModifier = Modifier.size(IosNavIconSize)

    Column(
        modifier = Modifier
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null,
            )
            // 与 iOS 胶囊的条目尺寸对齐：点击区 52dp 见方、内部左右 4dp。
            // iOS 是 64dp 一档（含 8dp 间隙），这里靠 miuix 悬浮栏自带的 12dp 项间距
            // 凑出同样的节奏（52 + 12 = 64），图标与文字的字号/间距也照 iOS 取值。
            .widthIn(min = IosNavItemSize)
            .height(IosNavItemSize)
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(IosNavIconLabelGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (badgeCount != null) {
            BadgedBox(
                badge = { Badge { Text(badgeCount.toString()) } },
            ) {
                Image(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = iconModifier,
                    colorFilter = ColorFilter.tint(contentColor),
                )
            }
        } else {
            Image(
                imageVector = icon,
                contentDescription = label,
                modifier = iconModifier,
                colorFilter = ColorFilter.tint(contentColor),
            )
        }
        if (showLabel) {
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 两个内容叠加，按自然宽度测量，容器宽度随 [progress] 插值。
 *
 * 容器宽 = lerp(导航宽, 操作宽)，内容始终居中；外层裁剪只作用于内容，
 * 胶囊背景/阴影/模糊由父组件按同一宽度绘制，所以三者始终同步。
 */
@Composable
private fun AnimatedBarContent(
    progress: Float,
    navContent: @Composable () -> Unit,
    detailContent: @Composable () -> Unit,
) {
    SubcomposeLayout { _ ->
        val navPlaceable = subcompose("nav") {
            Box(Modifier.alpha(1f - progress)) { navContent() }
        }.first().measure(Constraints())
        val detailPlaceable = subcompose("detail") {
            Box(Modifier.alpha(progress)) { detailContent() }
        }.first().measure(Constraints())

        val width = (navPlaceable.width + (detailPlaceable.width - navPlaceable.width) * progress)
            .roundToInt()
        val height = max(navPlaceable.height, detailPlaceable.height)

        layout(width, height) {
            val navX = (width - navPlaceable.width) / 2
            val navY = (height - navPlaceable.height) / 2
            val detailX = (width - detailPlaceable.width) / 2
            val detailY = (height - detailPlaceable.height) / 2
            // 后 place 的在上层：让当前可见内容位于顶层，避免不可见图层拦截点击
            // 动画结束（progress=0/1）时只放当前层：两层不重叠 → 不会拦截点击
            if (progress < 1f) navPlaceable.place(navX, navY)
            if (progress > 0f) detailPlaceable.place(detailX, detailY)
        }
    }
}
