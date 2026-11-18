// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.eros.shared.reader.PageImage
import app.eros.shared.reader.ReaderEngine
import app.eros.shared.reader.ReaderFit
import app.eros.shared.reader.ReaderMode
import app.eros.shared.reader.ReaderSession
import app.eros.shared.settings.AppSettings
import kotlinx.coroutines.flow.drop

/**
 * 翻页模式：单页 / 双页 / 右→左。双页只看模式选择，横竖屏都一样并排两页。
 *
 * 只负责渲染与 Pager 同步，手势由页面自身的 [ZoomableSlot] 接管：
 * 未缩放时 Pager 正常翻页，缩放态下 Pager 关闭滚动。
 */
@Composable
internal fun ReaderPager(
    session: ReaderSession,
    zoom: PageZoomState,
    taps: ZoomableTaps,
    /** 当前页面底色对应的前景色（占位环/提示文字跟随浅深色）。 */
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    val mode = session.mode
    val dual = session.pagedDual()
    val step = ReaderEngine.step(dual)
    val count = ReaderEngine.slotCount(session.totalPages, dual)
    // 单页模式要把整页放进屏幕：横屏时按宽度铺满会把上下裁掉，而单页又没有可以拖动的地方，
    // 裁掉的部分就看不到。双页模式左右各占一半，仍按设置里的适应方式铺开。
    val pageScale = if (dual) fitScale() else ContentScale.Fit
    // 单页 ↔ 双页切换时槽数会变（889 ↔ 445）。PagerState 是 remember 出来的，同一个
    // ReaderPager 组合位置会复用旧实例，而此时 initialPage 不再生效：旧的 currentPage
    // 会被夹到新槽数的末页（476 → 444），首帧直接跳到最后一页，快照又把 444*2=888
    // 写回 session，页码就飞了。用 dual 作 key 强制换新的 PagerState。
    val pagerState = key(dual) {
        rememberPagerState(
            initialPage = ReaderEngine.slotForPage(session.currentPage, session.totalPages, dual),
            pageCount = { count },
        )
    }

    // step 必须作为 key：从单页切到双页时 step 由 1 变 2，旧协程会把槽号当成页号写回，
    // 于是「滑到下一槽」被解析成第 1 页，sync 又把 Pager 拉回原槽 —— 表现为翻页失灵 + 闪屏。
    LaunchedEffect(pagerState, step) {
        snapshotFlow { pagerState.currentPage }.drop(1).collect { slot ->
            val page = slot * step
            if (page != session.currentPage) session.onSweepedTo(page)
        }
    }

    LaunchedEffect(session.currentPage, dual) {
        val target = ReaderEngine.slotForPage(session.currentPage, session.totalPages, dual)
        // 用户正在拖动时不回写，避免和 Pager 互相拉扯导致画面闪。
        if (!pagerState.isScrollInProgress && pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        reverseLayout = mode == ReaderMode.RTL,
        beyondViewportPageCount = 1,
        // 缩放态不翻页：必须先缩回 1 倍才能左右滑。
        userScrollEnabled = !zoom.isZoomed,
    ) { slot ->
        val pages = ReaderEngine.slotPages(slot, session.totalPages, dual)
        // 只有当前槽位接收缩放手势，相邻预组合页只画不缩放。
        val zoomForSlot = if (slot == pagerState.currentPage) zoom else null
        if (pages.size <= 1) {
            PageSlot(session, pages.firstOrNull() ?: 0, zoomForSlot, taps, foreground, pageScale, Modifier.fillMaxSize())
        } else {
            val content: @Composable () -> Unit = {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pages.forEach { page ->
                        PageSlot(
                            session = session,
                            index = page,
                            zoom = null,
                            taps = taps,
                            foreground = foreground,
                            fit = pageScale,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
            }
            if (zoomForSlot != null) {
                ZoomableSlot(
                    zoom = zoomForSlot,
                    modifier = Modifier.fillMaxSize(),
                    taps = taps,
                    content = content,
                )
            } else {
                content()
            }
        }
    }
}

/**
 * 连续模式：整列滚动，可见页回报给 session 记录进度。
 *
 * 每一页有独立的缩放状态：放大某一行不会连带放大其他行；
 * 放大后单指纵向拖到图片边界，超出的位移会转发给列表，可以继续往下滚。
 */
@Composable
internal fun ReaderVertical(
    session: ReaderSession,
    maxZoom: () -> Float,
    taps: ZoomableTaps,
    /** 当前页面底色对应的前景色（占位环/提示文字跟随浅深色）。 */
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = session.currentPage)
    val rowScope = rememberCoroutineScope()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.drop(1).collect { index ->
            session.onVisiblePage(index)
        }
    }
    LaunchedEffect(session.currentPage) {
        if (listState.firstVisibleItemIndex != session.currentPage) {
            listState.scrollToItem(session.currentPage)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        items(session.totalPages) { index ->
            val zoom = remember(index) { PageZoomState(maxZoom, rowScope) }
            LaunchedEffect(index) { session.loader.requestPage(index) }
            val img = session.loader.pageImages[index] ?: PageImage.Pending
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (img is PageImage.Ready) {
                    ZoomableSlot(
                        zoom = zoom,
                        modifier = Modifier.fillMaxWidth(),
                        taps = taps,
                        contentSize = { frame ->
                            drawnImageSize(img.bitmap, ContentScale.FillWidth, frame)
                        },
                        // 放大后纵向顶到图片边界还能继续上下滑，不会卡在被放大的这一页。
                        onVerticalScroll = { dy -> listState.dispatchRawDelta(dy) },
                    ) {
                        Image(
                            bitmap = img.bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(session.loader.pageRatio(index))
                            // 占位状态没有 ZoomableSlot，单独挂点击手势，否则这一页在加载时点不动屏幕。
                            .readerTaps(null, taps),
                        contentAlignment = Alignment.Center,
                    ) {
                        PageStatus(
                            img = img,
                            onRetry = { session.retry(index) },
                            foreground = foreground,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单页槽位：Ready 走可缩放渲染，其余显示占位状态。
 *
 * [zoom] 为 null 表示这一页只是预组合的邻居（或双页槽位的另一半），不接收缩放。
 */
@Composable
private fun PageSlot(
    session: ReaderSession,
    index: Int,
    zoom: PageZoomState?,
    taps: ZoomableTaps,
    foreground: Color,
    fit: ContentScale,
    modifier: Modifier,
) {
    LaunchedEffect(index) { session.loader.requestPage(index) }
    val img = session.loader.pageImages[index] ?: PageImage.Pending
    val contentScale = fit
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (img is PageImage.Ready) {
            val page: @Composable () -> Unit = {
                Image(
                    bitmap = img.bitmap,
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (zoom == null) {
                page()
            } else {
                ZoomableSlot(
                    zoom = zoom,
                    modifier = Modifier.fillMaxSize(),
                    taps = taps,
                    contentSize = { frame -> drawnImageSize(img.bitmap, contentScale, frame) },
                ) {
                    page()
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 同上：占位状态自己挂点击手势，保证加载中也能切栏。
                    .readerTaps(null, taps),
                contentAlignment = Alignment.Center,
            ) {
                PageStatus(
                    img = img,
                    onRetry = { session.retry(index) },
                    foreground = foreground,
                )
            }
        }
    }
}

/** 翻页模式的页面适应方式（设置项 `readerFit`）。 */
@Composable
private fun fitScale(): ContentScale = when (ReaderFit.fromOrdinal(AppSettings.readerFit)) {
    ReaderFit.WIDTH -> ContentScale.FillWidth
    ReaderFit.HEIGHT -> ContentScale.FillHeight
    ReaderFit.ORIGINAL -> ContentScale.None
}
