// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.ThumbUrl
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * 信息流封面预取：只要网格滚动，就把「当前可见最后一项之后 [ahead] 项」的封面提前入队，
 * 让快速下滑时下一屏的封面已经在下载/解码路上。
 *
 * 请求尺寸取当前可见项的实际宽度（与 GalleryCard 一致），不额外产生测量请求。
 */
@Composable
fun CoverPrefetchEffect(
    gridState: LazyGridState,
    items: List<GalleryUi>,
    ahead: Int = 12,
) {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val fallbackPx = with(density) { 190.dp.roundToPx() }
    val enqueued = remember { mutableSetOf<String>() }

    LaunchedEffect(gridState, items) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow null
            val width = info.visibleItemsInfo.firstOrNull()?.size?.width?.takeIf { it > 0 }
                ?: fallbackPx
            last to width
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { (lastVisible, widthPx) ->
                val start = lastVisible + 1
                if (start >= items.size) return@collect
                val end = minOf(items.size, start + ahead)
                val loader = SingletonImageLoader.get(context)
                for (index in start until end) {
                    val thumb = items[index].thumb ?: continue
                    val url = ThumbUrl.forCard(thumb)
                    if (!enqueued.add(url)) continue
                    loader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            .size(widthPx, widthPx * 4 / 3)
                            .build(),
                    )
                }
            }
    }
}
