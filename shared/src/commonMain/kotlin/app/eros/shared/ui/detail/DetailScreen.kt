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
import app.eros.shared.ui.ReadStore
import app.eros.shared.ui.ErosPageScaffold
import app.eros.shared.ui.ErosBarIcon
import app.eros.shared.ui.SeedHeader
import app.eros.shared.ui.DetailSkeleton
import app.eros.shared.ui.rememberPreviewColumns
import app.eros.shared.ui.TrackOverlay
import app.eros.shared.ui.ErosBottomSheet

@Composable
fun DetailScreen(
    state: DetailState,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onReadAt: (Int) -> Unit,
    onOpenComments: () -> Unit,
    onSearchTag: (String) -> Unit,
    onSearchUploader: (String) -> Unit,
    onOpenGallery: ((GalleryRef) -> Unit)? = null,
) {
    val barInset = LocalAppBottomBarInset.current
    val gridState = rememberLazyGridState()
    LaunchedEffect(state) {
        state.ensureLoaded()
        ReadStore.markRead(state.gid)
    }

    var tab by remember { mutableIntStateOf(0) }   // 0 评论 / 1 预览

    // 顶栏：滚过信息卡后显示画廊标题，长标题截断。
    val topTitle by remember {
        derivedStateOf {
            val d = state.detail
            val scrolled = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 32
            if (scrolled && d != null) {
                GalleryTitles.primary(d.title, d.titleJpn).let { if (it.length > 18) it.take(18) + "…" else it }
            } else {
                "画廊详情"
            }
        }
    }

    val nearBottom by remember {
        derivedStateOf {
            if (tab != 1) return@derivedStateOf false
            val info = gridState.layoutInfo
            state.previews.size > 0 && info.visibleItemsInfo.any { it.index == state.previews.size - 1 } && !state.loadingPreviews
        }
    }
    LaunchedEffect(nearBottom, state.previews.size, tab) {
        if (nearBottom) state.loadMorePreviews()
    }

    ErosPageScaffold(
        title = topTitle,
        onBack = onBack,
        // 评论 / 预览的分段 Tab 挂在顶栏底部（和排行榜、下载页同一套做法）：
        // 滚到哪儿都在，且它采样的是页面内容层、自己不在那一层里，不会自引用。
        tabs = state.detail?.let {
            listOf("评论 ${it.comments.size}", "预览 ${it.pages}")
        },
        selectedTab = tab,
        onTabSelected = { tab = it },
        actions = {
            val detail = state.detail
            if (detail != null) {
                ErosBarIcon(
                    onClick = {
                        // 只分享链接，标题由接收方自己解析，避免各平台拼出难看的纯文本。
                        app.eros.shared.platformShare(
                            "https://e-hentai.org/g/${detail.gid}/${state.token}/",
                        )
                    },
                ) { Icon(imageVector = MiuixIcons.Share, contentDescription = "分享") }
            }
        },
    ) { innerPadding, pageScrollBehavior, pageBackdrop ->
        Box(
            Modifier
                .fillMaxSize()
        ) {
            when {
                state.detail == null -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = innerPadding.calculateTopPadding()),
                ) {
                    state.seed?.let { seed ->
                        Box(Modifier.padding(horizontal = 12.dp)) {
                            SeedHeader(seed, showPlaceholders = state.loading)
                        }
                    }
                    if (state.loading) {
                        DetailSkeleton(Modifier.padding(horizontal = 12.dp), includeInfo = state.seed == null)
                    } else {
                        // 不可重试的错误（已删除/隐藏/需登录）不做成可点区域，避免"点了没反应"。
                        val retryable = state.lastError?.isRetryable() ?: true
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .then(if (retryable) Modifier.clickable { state.reload() } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                state.error ?: if (retryable) "加载失败，点按重试" else "加载失败",
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                }

                else -> {
                    val detail = state.detail!!
                    LazyVerticalGrid(
                        // 预览格子里是整页图，比列表多一列：手机仍是 3 列，平板/横屏自动 4–5 列。
                        columns = GridCells.Fixed(rememberPreviewColumns()),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(pageScrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = innerPadding.calculateTopPadding() + 12.dp,
                            bottom = barInset + 16.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "gallery") {
                            GalleryInfoSection(detail, favoriteCount = state.favoriteCount, onSearchTag = onSearchTag, onSearchUploader = onSearchUploader)
                        }

                        if (detail.tags.any { it.tags.isNotEmpty() }) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "tags") {
                                TagsSection(
                                    detail = detail,
                                    onSearchTag = onSearchTag,
                                )
                            }
                        }

                        if (tab == 0) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "comments") {
                                CommentsSection(detail, onOpenComments, onOpenGallery)
                            }
                        } else {
                            items(state.displayedPreviews(), key = { it.index }) { thumb ->
                                ThumbCell(thumb) { onReadAt(thumb.index) }
                            }
                            if (state.loadingPreviews) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "previewLoading") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { CircularProgressIndicator() }
                                }
                            } else if (state.allPreviewsLoaded && state.previews.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "previewEnd") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "已全部加载（${state.previews.size} 张）",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        if (state.detail != null) {
            TrackOverlay(state.favSheet)
            ErosBottomSheet(
                show = state.favSheet,
                title = if (state.favorited) "收藏夹" else "收藏到",
                insideMargin = DpSize(16.dp, 4.dp),
                onDismissRequest = { state.closeFavSheet() },
                onDismissFinished = { },
            ) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    repeat(10) { idx ->
                        FavCatRow(
                            name = state.favCatNames[idx],
                            count = state.favCatCounts[idx],
                            selected = idx == state.favSelected,
                            onClick = { state.favoriteTo(idx) },
                        )
                    }
                    if (state.favorited) {
                        HorizontalDivider()
                        FavCatRow(
                            name = "取消收藏",
                            count = -1,
                            selected = false,
                            destructive = true,
                            onClick = { state.unfavorite() },
                        )
                    }
                }
            }
        }
        }
    }
}

/** 画廊信息：SmallTitle + Card，封面左、标题右。 */
