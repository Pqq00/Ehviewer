// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.cache.DetailPrefetcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 可见项静止 300ms 后预取前两张的详情；滚动变化时取消上一批。
 */
@Composable
fun DetailPrefetchEffect(
    gridState: LazyGridState,
    items: List<GalleryUi>,
) {
    LaunchedEffect(gridState, items) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collectLatest { indices ->
                delay(300)
                indices.take(2).forEach { index ->
                    items.getOrNull(index)?.let { DetailPrefetcher.prefetch(it.gid, it.token) }
                }
            }
    }
}
