// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import app.eros.shared.data.ThumbUrl
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.hippo.ehviewer.util.AppLog
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 一张封面图的加载状态：缩略图 → 原图回退 → 失败，外加 4 秒超时与点击重试。
 *
 * 网格卡和下载卡共用同一套语义，避免两边各写一份状态机后行为悄悄分叉
 * （下载卡此前没有超时与重试，弱网下会长期停在骨架）。
 */
@Stable
internal class GalleryCoverState {
    var ready by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var timedOut by mutableStateOf(false)
    var useRaw by mutableStateOf(false)
    var retryToken by mutableIntStateOf(0)

    /** 是否已给出确定结果（成功 / 失败 / 超时）：用于结束骨架与入场动画。 */
    val revealed: Boolean get() = ready || failed || timedOut

    fun markReady() {
        ready = true
    }

    fun markFailed() {
        failed = true
    }

    /** 失败后重试：清掉失败态并通过 [retryToken] 重新发起请求。 */
    fun retry() {
        ready = false
        failed = false
        timedOut = false
        retryToken += 1
    }
}

/** 按缩略图地址记住封面状态；换图即重置，并启动 4 秒超时兜底。 */
@Composable
internal fun rememberGalleryCoverState(thumb: String?): GalleryCoverState {
    val state = remember(thumb) { GalleryCoverState() }
    LaunchedEffect(thumb, state) {
        if (thumb != null) {
            delay(4_000)
            state.timedOut = true
        }
    }
    return state
}

/**
 * 按给定像素尺寸请求并绘制封面。
 *
 * 尺寸由调用方按布局算好传入：网格卡按卡片像素宽，下载卡按固定封面尺寸，
 * 避免解码整张原图。模糊与圆角由外层的容器负责。
 */
@Composable
internal fun GalleryCoverImage(
    state: GalleryCoverState,
    thumb: String?,
    requestWidth: Int,
    requestHeight: Int,
    modifier: Modifier = Modifier,
    contentAlpha: Float = 1f,
) {
    if (thumb == null) return
    val context = LocalPlatformContext.current
    // 缩略图加载失败时退回原图再试一次；两次都失败才算封面失败。
    val url = remember(thumb, state.useRaw) {
        if (state.useRaw) thumb else ThumbUrl.forCard(thumb)
    }
    key(state.retryToken) {
        val request = remember(url, requestWidth, requestHeight) {
            ImageRequest.Builder(context)
                .data(url)
                .size(requestWidth.coerceAtLeast(1), requestHeight.coerceAtLeast(1))
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { state.markReady() },
            onError = {
                if (!state.useRaw) {
                    AppLog.d("Thumb", "fallback to original: $thumb")
                    state.useRaw = true
                } else {
                    state.markFailed()
                }
            },
            placeholder = ColorPainter(MiuixTheme.colorScheme.surfaceVariant),
            modifier = modifier
                .graphicsLayer { alpha = contentAlpha }
                .coverBlur(),
        )
    }
}
