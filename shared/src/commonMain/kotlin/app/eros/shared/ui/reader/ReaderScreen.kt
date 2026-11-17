// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import app.eros.shared.reader.ReaderEngine
import app.eros.shared.reader.ReaderMode
import app.eros.shared.reader.ReaderSession
import app.eros.shared.reader.ReaderTap
import app.eros.shared.reader.readerVolumeKeys
import app.eros.shared.settings.AppSettings
import app.eros.shared.system.setStatusBarHidden
import app.eros.shared.ui.rememberBarBackdrop
import kotlinx.coroutines.delay
import kotlin.math.min
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 阅读器主屏。
 *
 * 方案 B：内容区始终占满全屏，顶底栏「浮」在页面之上（底栏是悬浮胶囊）。
 * 因为 chrome 显隐不改变内容的可用空间，所以栏体可以做淡入淡出 + 轻微位移而完全不跳。
 *
 * 页面底色随 chrome 显隐变化：浅色主题下，栏体显示时白底（与栏体同为浅色），
 * 栏体收起后转为黑底（沉浸阅读）；深色主题始终黑底。顶底栏用主题 surface，开启「栏模糊」时改为背景模糊。
 */
@Composable
fun ReaderScreen(session: ReaderSession, onBack: () -> Unit) {
    LaunchedEffect(session) { session.ensureLoaded() }

    // 沉浸阅读：状态栏跟着顶底栏一起收放——栏体收起（纯看图）时连状态栏一起隐藏，
    // 点一下唤出栏体时状态栏也回来，顶栏就不会挤在挖孔区里。
    // 开关关掉时状态栏始终保留；离开阅读器一定恢复，避免把隐藏状态带到列表页。
    val hideStatusBar = AppSettings.readerHideStatusBar && !session.chromeVisible
    LaunchedEffect(hideStatusBar) {
        setStatusBarHidden(hideStatusBar)
    }
    DisposableEffect(Unit) {
        onDispose { setStatusBarHidden(false) }
    }

    val windowWidth = LocalWindowInfo.current.containerSize.width
    LaunchedEffect(windowWidth) {
        if (windowWidth > 0) session.loader.screenWidthPx = windowWidth
    }

    // 轻提示：底栏常驻悬浮，Snackbar 会被挡在栏下，改用页面上方浮出的胶囊提示。
    // 排队消息（保存/重试等有先后关系的）逐条播放；模式/适应方式这类状态提示走
    // session.transientHint，立即覆盖上一条，连点不会积压成延迟。
    var queueHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session) {
        for (message in session.messages) {
            queueHint = message
            delay(1600)
            queueHint = null
        }
    }
    val transientHint = session.transientHint
    LaunchedEffect(transientHint) {
        if (transientHint != null) {
            delay(900)
            session.clearTransient(transientHint)
        }
    }
    val hint = transientHint ?: queueHint

    val backdrop = rememberBarBackdrop()
    val isDarkTheme = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    // 浅色主题下栏体显示时白底，栏体收起后转黑底（沉浸）；深色主题始终黑底。底色用 220ms 交叉淡变。
    val whitePage = !isDarkTheme && session.chromeVisible
    val pageBackground by animateColorAsState(
        targetValue = if (whitePage) Color.White else Color.Black,
        animationSpec = tween(220),
        label = "readerPageBackground",
    )
    val pageTextColor by animateColorAsState(
        targetValue = if (whitePage) Color.Black else Color.White,
        animationSpec = tween(220),
        label = "readerPageText",
    )

    // 缩放属于「页」而不是「会话」：翻页后自动复位，相邻预组合页不会被一起放大。
    val maxZoom: () -> Float = { AppSettings.readerMaxZoom.coerceIn(2, 6).toFloat() }
    val zoomScope = rememberCoroutineScope()
    val zoom = remember(session, zoomScope) { PageZoomState(maxZoom, zoomScope) }
    LaunchedEffect(session.currentPage) { zoom.reset() }

    Box(Modifier.fillMaxSize().background(pageBackground)) {
        val landscape = isLandscape()
        val tapStep = ReaderEngine.step(session.pagedDual(landscape))
        // 点按/双击/长按挂在每一页上（见 ZoomableSlot 的 taps 参数）：
        // 连续模式下每页各有自己的缩放状态，屏幕层拿不到，双击就放大不了当前页。
        val taps = remember(session, landscape, tapStep) {
            ZoomableTaps(
                onTap = { zoomState, fraction, position ->
                    if (zoomState != null && zoomState.isZoomed) {
                        // 缩放态单击先平滑退回 1 倍，不翻页。
                        zoomState.zoomTo(target = 1f, centroid = position)
                    } else {
                        when (ReaderEngine.tapAction(fraction, AppSettings.readerTapPaging)) {
                            ReaderTap.Previous -> session.goTo(session.currentPage - tapStep)
                            ReaderTap.Next -> session.goTo(session.currentPage + tapStep)
                            ReaderTap.ToggleChrome -> session.toggleChrome()
                        }
                    }
                },
                onDoubleTap = { zoomState, position ->
                    // 占位状态（加载中/失败）没有缩放状态，双击不做任何事。
                    if (zoomState != null && AppSettings.readerDoubleTapZoom) {
                        // 按点击点放大；已放大则还原。
                        zoomState.zoomTo(
                            target = if (zoomState.isZoomed) 1f else min(2f, maxZoom()),
                            centroid = position,
                        )
                    }
                },
                onLongPress = { session.showChrome() },
                onSwipePast = { direction ->
                    // 缩放态下顶到图片边界继续滑：换算成翻页（翻页后缩放自动复位）。
                    session.goTo(session.currentPage + direction * tapStep)
                },
            )
        }

        Scaffold(
            // 容器透出外层页面底色；顶底栏不占槽位（方案 B：浮层）。
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageBackground)
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .onSizeChanged { if (it.width > 0) session.loader.screenWidthPx = it.width }
                    .readerVolumeKeys(
                        enabled = AppSettings.readerVolumeKeys,
                        onPrevious = { session.pageBy(-1, landscape) },
                        onNext = { session.pageBy(1, landscape) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val mode = session.pagedMode(landscape)
                when {
                    session.loader.urlsByIndex.isEmpty() && session.loader.error != null -> Text(
                        "加载失败：${session.loader.error}",
                        style = MiuixTheme.textStyles.body2,
                        color = pageTextColor,
                    )

                    // 不再用整块转圈替代内容：那会让「进阅读器第一秒」整屏没有任何可点区域，
                    // 顶底栏切不动。这里照常渲染页面，占位状态自己会显示页内环/「获取地址…」。
                    mode == ReaderMode.VERTICAL -> ReaderVertical(
                        session = session,
                        maxZoom = maxZoom,
                        taps = taps,
                        foreground = pageTextColor,
                    )

                    else -> ReaderPager(
                        session = session,
                        landscape = landscape,
                        zoom = zoom,
                        taps = taps,
                        foreground = pageTextColor,
                    )
                }
            }
        }

        // 顶底栏浮在内容之上（方案 B）；显隐用淡入淡出 + 轻微位移，内容空间不变所以不会跳。
        AnimatedVisibility(
            visible = session.chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 4 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 4 },
        ) {
            ReaderTopBar(session = session, onBack = onBack, backdrop = backdrop)
        }
        AnimatedVisibility(
            visible = session.chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 4 },
        ) {
            ReaderBottomBar(session = session, backdrop = backdrop)
        }

        // 轻提示胶囊：不随 chrome 显隐，始终浮在底栏上方。
        hint?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 148.dp),
            ) {
                ReaderHint(text = message)
            }
        }

        ReaderSheets(session = session, backdrop = backdrop)
    }
}

/** 当前是否横屏（跨平台，不依赖 Android Configuration）。 */
@Composable
private fun isLandscape(): Boolean {
    val density = LocalDensity.current
    val size = LocalWindowInfo.current.containerSize
    return with(density) { size.width.toDp() > size.height.toDp() }
}
