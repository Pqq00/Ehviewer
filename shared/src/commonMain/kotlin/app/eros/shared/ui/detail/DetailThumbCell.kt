// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.detail

import app.eros.shared.data.GalleryTitles
import com.hippo.ehviewer.util.AppLog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import app.eros.shared.data.EhApi
import app.eros.shared.data.GalleryDetailUi
import app.eros.shared.data.GalleryRef
import app.eros.shared.currentTimeMillis
import app.eros.shared.data.cache.DetailMemoryCache
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.data.cache.TagRecord
import app.eros.shared.data.EhError
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.PreviewThumbUi
import app.eros.shared.data.createEhApi
import app.eros.shared.data.isRetryable
import app.eros.shared.data.withEhError
import app.eros.shared.data.userMessage
import app.eros.shared.filter.FilterMode
import app.eros.shared.filter.FilterRule
import app.eros.shared.settings.AppSettings
import app.eros.shared.system.showSystemToast
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import app.eros.shared.ui.chrome.LocalAppBottomBarInset
import app.eros.shared.ui.RemoteImage

@Composable
internal fun ThumbCell(thumb: PreviewThumbUi, onClick: () -> Unit) {
    // 雪碧图分片的宽高比优先（横向），无裁剪信息时退回竖版
    val aspect = if (thumb.clipWidth > 0 && thumb.clipHeight > 0) {
        thumb.clipWidth.toFloat() / thumb.clipHeight
    } else 0.72f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .squircleClip(cornerRadius = 8.dp)
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
    ) {
        SpriteThumb(thumb)
        Text(
            "${thumb.index + 1}",
            style = MiuixTheme.textStyles.footnote2,
            color = Color.White,
            modifier = Modifier
                .padding(3.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                .padding(horizontal = 4.dp),
        )
    }
}

/**
 * 预览缩略图：E-Hentai 的预览图是一张"雪碧图"，多页共用同一张图，
 * 通过 `offsetX/offsetY/clipWidth/clipHeight` 切出每一页。
 */
@Composable
private fun SpriteThumb(thumb: PreviewThumbUi) {
    val painter = rememberAsyncImagePainter(model = thumb.thumbUrl)
    val painterState by painter.state.collectAsState()
    if (thumb.clipWidth <= 0 || thumb.clipHeight <= 0) {
        RemoteImage(url = thumb.thumbUrl, contentScale = ContentScale.Crop, failureText = "×")
        return
    }
    if (painterState !is AsyncImagePainter.State.Success) {
        RemoteImage(url = thumb.thumbUrl, contentScale = ContentScale.Crop, failureText = "×")
        return
    }
    Canvas(Modifier.fillMaxSize()) {
        val intrinsic = painter.intrinsicSize
        val iw = intrinsic.width
        val ih = intrinsic.height
        if (!iw.isFinite() || !ih.isFinite() || iw <= 0f || ih <= 0f) return@Canvas
        val scaleFactor = size.width / thumb.clipWidth.toFloat()
        clipRect(0f, 0f, size.width, size.height) {
            scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
                translate(-thumb.offsetX.toFloat(), -thumb.offsetY.toFloat()) {
                    with(painter) { draw(size = intrinsic) }
                }
            }
        }
    }
}

