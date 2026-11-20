// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eros.shared.data.GalleryUi
import app.eros.shared.settings.AppSettings
import app.eros.shared.ui.chrome.LocalAppBottomBarInset
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 画廊分页网格：首页 / 排行榜 / 收藏 / 搜索结果共用的同一份骨架。
 *
 * 抽出前四处是逐行复制的（每处约 140 行）：下拉刷新、列数计算、网格、首屏骨架、
 * 空态/错误态、加载更多与「没有更多了」footer。行为差异只允许通过参数表达：
 *
 * - 首屏骨架：始终由 [SkeletonGridOverlay] 整层覆盖并淡出，网格里不画骨架项；
 * - 列数：按容器实测宽度算（玻璃侧栏展开时窗口宽 ≠ 内容宽），列表布局沿用
 *   `cardStyle == "download"` 的分支；
 * - 空态：`emptyTitle` 为空表示该页不展示空态（排行榜的列表总是有内容）；
 * - `header` 同时作为骨架的首项，页面顶部那条 chips（分类/收藏夹）滚动时不会跟着骨架闪烁。
 *
 * 需要「筛选条固定不滚」的页面不要用本组件，改用可滚动 header 外的固定行。
 */
@Composable
fun PagedGalleryGrid(
    items: List<GalleryUi>,
    gridState: LazyGridState,
    isRefreshing: Boolean,
    initialLoading: Boolean,
    loadingMore: Boolean,
    endReached: Boolean,
    error: String?,
    innerPadding: PaddingValues,
    pageScrollBehavior: ScrollBehavior,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onItemClick: (GalleryUi) -> Unit,
    modifier: Modifier = Modifier,
    emptyTitle: String = "",
    emptyDescription: String = "",
    emptyHeight: Dp = 220.dp,
    header: (@Composable () -> Unit)? = null,
    skeletonHeader: (@Composable () -> Unit)? = header,
) {
    val barInset = LocalAppBottomBarInset.current
    val contentPadding = PaddingValues(
        start = GalleryGridSidePadding,
        end = GalleryGridSidePadding,
        top = innerPadding.calculateTopPadding() + GalleryGridSidePadding,
        bottom = barInset + GalleryGridSidePadding,
    )
    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        topAppBarScrollBehavior = pageScrollBehavior,
    ) {
        // 列数按容器实测宽度算：rail 展开时窗口宽不等于内容宽，按窗口算会多一列、
        // 卡片被压窄。这里扣掉页面左右各 12dp 的内边距，剩下的就是网格真正能用的宽度。
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = if (AppSettings.cardStyle == "download") {
                rememberListColumns(maxWidth - GalleryGridSidePadding * 2)
            } else {
                rememberGalleryColumns(maxWidth - GalleryGridSidePadding * 2)
            }
            // 始终是同一棵网格；首屏的骨架单独一层覆盖在上面（见 SkeletonGridOverlay），
            // 内容到达时整层淡出，下拉刷新和滚动在首屏就可用。
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pageScrollBehavior.nestedScrollConnection),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(GalleryGridSidePadding),
                verticalArrangement = Arrangement.spacedBy(GalleryGridSidePadding),
            ) {
                if (header != null) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "header") { header() }
                }

                when {
                    // 首屏加载不在这里画骨架：外面那层 [SkeletonGridOverlay] 负责呼吸，
                    // 内容到达时整层淡出，避免「骨架项 → 卡片」同帧硬切。
                    initialLoading -> Unit

                    items.isEmpty() && error != null -> {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
                            ErosErrorState(
                                message = error,
                                onRetry = onRetry,
                                modifier = Modifier.fillMaxWidth().height(emptyHeight),
                            )
                        }
                    }

                    items.isEmpty() && emptyTitle.isNotEmpty() -> {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                            ErosEmptyState(
                                title = emptyTitle,
                                description = emptyDescription,
                                modifier = Modifier.fillMaxWidth().height(emptyHeight),
                            )
                        }
                    }

                    else -> {
                        itemsIndexed(items, key = { _, it -> it.gid }) { index, gallery ->
                            GalleryCard(
                                gallery = gallery,
                                index = index,
                                scrolling = gridState.isScrollInProgress,
                                onClick = { onItemClick(gallery) },
                            )
                        }

                        if (loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "loadingMore") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        // 下一页失败时给一个就地重试入口：此前有的页面静默、有的页面自己写一份，
                        // 现在统一在这一层。
                        if (error != null) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "loadMoreError") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "加载失败，点按重试",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.clickable(onClick = onRetry),
                                    )
                                }
                            }
                        }

                        if (endReached) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "end") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "没有更多了",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 首屏骨架整层覆盖在网格上方，内容到达时淡出（180ms），而不是把骨架项原地换掉。
            SkeletonGridOverlay(
                visible = initialLoading,
                columns = columns,
                contentPadding = contentPadding,
                header = { skeletonHeader?.invoke() },
            )
        }
    }
}
