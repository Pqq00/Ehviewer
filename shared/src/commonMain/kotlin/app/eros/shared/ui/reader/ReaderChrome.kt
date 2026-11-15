// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.eros.shared.reader.ReaderEngine
import app.eros.shared.reader.ReaderSession
import app.eros.shared.reader.ReaderSheet
import app.eros.shared.settings.AppSettings
import app.eros.shared.ui.BarBlurStyle
import app.eros.shared.ui.barBlurColors
import app.eros.shared.ui.currentBarBlurStyle
import kotlinx.coroutines.launch
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDefaults
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏底：跟其他页面一样，`backdrop` 为 null（栏模糊关闭 / 设备不支持）时铺主题 surface，
 * 否则用高斯 / 渐进模糊，形状固定为矩形（整条横跨）。
 */
@Composable
private fun Modifier.readerTopBarSurface(backdrop: LayerBackdrop?): Modifier {
    if (backdrop == null) {
        return background(MiuixTheme.colorScheme.surface)
    }
    val blur = if (currentBarBlurStyle() == BarBlurStyle.PROGRESSIVE) {
        Modifier.progressiveTextureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            gradient = ProgressiveBlur.Top.copy(curve = 1.8f),
            blurRadius = 14f,
            colors = barBlurColors(0.5f),
        )
    } else {
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            blurRadius = 25f,
            colors = barBlurColors(),
        )
    }
    return then(blur).background(Color.Transparent)
}

/**
 * 常驻悬浮的进度栏底：`backdrop` 为 null 时铺 `surfaceContainer` 实色，否则用胶囊玻璃模糊。
 * 形状与模糊参数跟底部悬浮导航栏（[FloatingNavigationBar]）保持一致，圆角由调用方按状态传入。
 */
@Composable
private fun Modifier.readerFloatingSurface(
    backdrop: LayerBackdrop?,
    shape: Shape = RoundedCornerShape(percent = 50),
): Modifier {
    if (backdrop == null) {
        return background(MiuixTheme.colorScheme.surfaceContainer)
    }
    val dark = isSystemInDarkTheme()
    val highlight = remember(dark) {
        if (dark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
    }
    return textureBlur(
        backdrop = backdrop,
        shape = shape,
        blurRadius = 25f,
        colors = barBlurColors(0.6f, useSurfaceContainer = true),
        highlight = highlight,
    ).background(Color.Transparent)
}

/**
 * 顶栏：返回 + 页码。页码用一层透明命中区盖住标题，点按弹「跳转到页面」。
 */
@Composable
internal fun ReaderTopBar(
    session: ReaderSession,
    onBack: () -> Unit,
    backdrop: LayerBackdrop? = null,
) {
    Box(modifier = Modifier.fillMaxWidth().readerTopBarSurface(backdrop)) {
        SmallTopAppBar(
            title = "${session.currentPage + 1} / ${session.totalPages}",
            color = Color.Transparent,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(MiuixIcons.Back, contentDescription = "返回")
                }
            },
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(52.dp)
                .width(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    session.keepChrome()
                    session.openSheet(ReaderSheet.Jump)
                },
        )
        if (backdrop == null) {
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * 常驻悬浮进度栏。
 *
 * 常规态：胶囊里是「上一页 / 轨道 / 下一页」，首尾页对应的按钮置灰。
 * 按下即展开（不需要先长按）：胶囊从 44dp 长到 60dp、两端按钮淡出、两端浮出页码，
 * 拖动实时跟手；抬手跳页并收回。只是轻点（没有拖动）时只展开看看、不跳页，避免误触。
 */
@Composable
private fun ReaderProgressBar(
    session: ReaderSession,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    val total = session.totalPages.coerceAtLeast(1)
    val step = ReaderEngine.step(session.pagedDual(isReaderLandscape()))
    var dragTarget by remember(total) { mutableFloatStateOf(session.currentPage.toFloat()) }
    LaunchedEffect(session.currentPage) {
        dragTarget = session.currentPage.toFloat()
    }
    var dragging by remember { mutableStateOf(false) }
    // 只有真正拖动后才让页码跟手；单纯按住（还没移动）仍显示当前页，避免一按就跳到手指位置。
    var following by remember { mutableStateOf(false) }
    val armed = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val settled by animateFloatAsState(
        targetValue = ((session.currentPage + 1f) / total.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(220),
        label = "readerProgress",
    )
    val progress = if (following) ((dragTarget + 1f) / total.toFloat()).coerceIn(0f, 1f) else settled

    val capsuleHeight = lerp(44.dp, 58.dp, armed.value)
    // 常规态是完美胶囊；展开态换成固定大圆角（跟参考图与悬浮导航栏一致，不是半圆）。
    val cornerRadius = lerp(capsuleHeight / 2f, 18.dp, armed.value)
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val trackHeight = lerp(8.dp, 12.dp, armed.value)
    val trackInset = lerp(44.dp, 16.dp, armed.value)
    val buttonAlpha = (1f - armed.value).coerceIn(0f, 1f)
    val numbersAlpha = armed.value.coerceIn(0f, 1f)
    // 展开时轨道略往下让位给上行页码；平时保持居中。
    val trackCenterFraction = 0.5f + 0.14f * armed.value
    val trackColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val activeColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    val iconColor = MiuixTheme.colorScheme.onSurface
    val canPrev = session.currentPage > 0
    val canNext = session.currentPage < total - 1

    // 外层高度固定 58dp：展开时只让胶囊在内部长高，避免底栏跟着重排、
    // 手指下的坐标随布局位移漂移（那会把「点击」误判成「拖动」）。
    Box(
        modifier = modifier.height(58.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(capsuleHeight)
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 10.dp,
                        color = Color.Black,
                        alpha = 0.2f,
                    ),
                )
                .clip(shape)
                .readerFloatingSurface(backdrop, shape)
                .pointerInput(total) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width.toFloat()
                        val edge = 44.dp.toPx()
                        // 左侧/右侧 44dp 是翻页按钮区：点击直接翻页，不展开轨道。
                        if (down.position.x < edge || down.position.x > width - edge) {
                            val next = down.position.x > width - edge
                            down.consume()
                            // 用 waitForUpOrCancellation 而不是自己轮询：快速点击时 UP 可能和
                            // DOWN 落在同一批事件里，自己 await 会一直等下一次触摸。
                            val up = waitForUpOrCancellation()
                            up?.consume()
                            if (up != null && (if (next) canNext else canPrev)) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                session.goTo(session.currentPage + if (next) step else -step)
                            }
                            return@awaitEachGesture
                        }
                        // 按下即展开；拖动时用当前（正在变宽的）轨道换算目标页，保证跟手。
                        fun targetAt(x: Float): Float {
                            val inset = lerp(44.dp, 16.dp, armed.value).toPx()
                            val usable = (width - inset * 2f).coerceAtLeast(1f)
                            val ratio = ((x - inset) / usable).coerceIn(0f, 1f)
                            return ratio * (total - 1).toFloat()
                        }
                        dragging = true
                        following = false
                        session.keepChrome()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch { armed.animateTo(1f, tween(120)) }
                        val start = down.position
                        var draggedX = 0f
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            draggedX = maxOf(draggedX, abs(change.position.x - start.x))
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            if (draggedX > viewConfiguration.touchSlop) {
                                following = true
                                dragTarget = targetAt(change.position.x)
                            }
                            change.consume()
                        }
                        dragging = false
                        following = false
                        scope.launch { armed.animateTo(0f, tween(160)) }
                        if (draggedX > viewConfiguration.touchSlop) {
                            session.goTo(dragTarget.toInt())
                        } else {
                            dragTarget = session.currentPage.toFloat()
                        }
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val inset = trackInset.toPx()
                val usable = (size.width - inset * 2f).coerceAtLeast(1f)
                val track = trackHeight.toPx()
                val centerY = size.height * trackCenterFraction
                val radius = CornerRadius(track / 2f, track / 2f)
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(inset, centerY - track / 2f),
                    size = Size(usable, track),
                    cornerRadius = radius,
                )
                val filled = usable * progress
                if (filled > 0f) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(inset, centerY - track / 2f),
                        size = Size(filled, track),
                        cornerRadius = radius,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp)
                    .alpha(buttonAlpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier.width(32.dp).height(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = "上一页",
                        tint = iconColor.copy(alpha = if (canPrev) 1f else 0.25f),
                    )
                }
                Box(
                    modifier = Modifier.width(32.dp).height(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronForward,
                        contentDescription = "下一页",
                        tint = iconColor.copy(alpha = if (canNext) 1f else 0.25f),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 9.dp)
                    .alpha(numbersAlpha),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${dragTarget.toInt() + 1}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = iconColor,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$total",
                    style = MiuixTheme.textStyles.footnote1,
                    color = iconColor.copy(alpha = 0.55f),
                )
            }
        }
    }
}

/** 阅读器窗口是否为横屏；进度栏用它决定双页下的翻页步长。 */
@Composable
private fun isReaderLandscape(): Boolean {
    val density = LocalDensity.current
    val size = LocalWindowInfo.current.containerSize
    return with(density) { size.width.toDp() > size.height.toDp() }
}

/**
 * 底栏按钮：保存 / 阅读设置 / 翻页模式。按应用导航栏样式走固定（[NavigationBar]，自带 64dp 高）或悬浮（[FloatingNavigationBar]）。
 */
@Composable
private fun ReaderActionBar(session: ReaderSession, backdrop: LayerBackdrop?) {
    val displayMode = when (AppSettings.navBarMode) {
        1 -> NavigationBarDisplayMode.IconOnly
        2 -> NavigationBarDisplayMode.IconWithSelectedLabel
        else -> NavigationBarDisplayMode.IconAndText
    }
    val onSave: () -> Unit = { session.save() }
    val onShare: () -> Unit = { session.share() }
    val onSettings: () -> Unit = {
        session.keepChrome()
        session.openSheet(ReaderSheet.Settings)
    }
    val onMode: () -> Unit = {
        session.keepChrome()
        session.cycleMode()
    }

    if (AppSettings.navBarStyle == 0) {
        val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (backdrop != null) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            blurRadius = 25f,
                            colors = barBlurColors(),
                        )
                    } else {
                        Modifier
                    },
                )
                .background(barColor),
        ) {
            NavigationBar(color = barColor, mode = displayMode) {
                NavigationBarItem(
                    selected = false,
                    onClick = onShare,
                    icon = MiuixIcons.Share,
                    label = "发送",
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSave,
                    icon = MiuixIcons.Download,
                    label = "保存",
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSettings,
                    icon = MiuixIcons.Settings,
                    label = "阅读设置",
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onMode,
                    icon = MiuixIcons.Refresh,
                    label = session.mode.label,
                )
            }
        }
    } else {
        val shape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius)
        val dark = isSystemInDarkTheme()
        val highlight = remember(dark) {
            if (dark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
        }
        FloatingNavigationBar(
            modifier = if (backdrop != null) {
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = shape,
                    blurRadius = 25f,
                    colors = barBlurColors(0.6f, useSurfaceContainer = true),
                    highlight = highlight,
                )
            } else {
                Modifier
            },
            color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FloatingNavigationBarDefaults.ItemSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FloatingNavigationBarItem(
                    selected = false,
                    onClick = onShare,
                    icon = MiuixIcons.Share,
                    label = "发送",
                )
                FloatingNavigationBarItem(
                    selected = false,
                    onClick = onSave,
                    icon = MiuixIcons.Download,
                    label = "保存",
                )
                FloatingNavigationBarItem(
                    selected = false,
                    onClick = onSettings,
                    icon = MiuixIcons.Settings,
                    label = "阅读设置",
                )
                FloatingNavigationBarItem(
                    selected = false,
                    onClick = onMode,
                    icon = MiuixIcons.Refresh,
                    label = session.mode.label,
                )
            }
        }
    }
}

/**
 * 阅读器底部：上方常驻进度栏 + 下方按钮栏（固定 / 悬浮两档）。
 */
@Composable
internal fun ReaderBottomBar(session: ReaderSession, backdrop: LayerBackdrop? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ReaderProgressBar(
            session = session,
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        ReaderActionBar(session = session, backdrop = backdrop)
    }
}

/**
 * 阅读器轻提示（保存结果 / 模式切换等）。
 *
 * 底栏与进度栏都是常驻浮层，Snackbar 会被压在栏下，因此改用页面上浮出的胶囊。
 */
@Composable
internal fun ReaderHint(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            color = Color.White,
        )
    }
}
