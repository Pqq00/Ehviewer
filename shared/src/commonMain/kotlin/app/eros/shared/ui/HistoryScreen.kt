// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import app.eros.shared.data.GalleryTitles
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eros.shared.currentTimeMillis
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.data.cache.HistoryEntry
import app.eros.shared.settings.AppSettings
import app.eros.shared.ui.chrome.ErosSelectionToolbar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** P2：浏览历史列表，数据来自 `GalleryCache.history()`（gallery 表 JOIN 标题/封面）。 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpen: (HistoryEntry) -> Unit,
    /** 直接续读：从记录里的页码进阅读器（点卡片本身仍然先进详情）。 */
    onRead: (HistoryEntry) -> Unit,
) {
    var items by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 多选：长按任意一条进入，顶栏变「已选 N 项」，可全选 / 批量删除。
    val selection = rememberSelectionState<Long>()
    val selecting = selection.selecting
    var confirming by remember { mutableStateOf(false) }

    fun exitSelection() = selection.exit()

    fun toggle(gid: Long) = selection.toggle(gid)

    fun reload() {
        scope.launch {
            loading = true
            items = runCatching { GalleryCache.history(200) }.getOrDefault(emptyList())
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val allSelected = selection.allSelected(items.map { it.gid })

    // 多选态：左上角关闭、右上角全选/取消全选，删除放在底部 FloatingToolbar。
    val navigationIcon: (@Composable () -> Unit)? = if (selecting) {
        {
            ErosBarIcon(onClick = { exitSelection() }) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = "退出多选")
            }
        }
    } else {
        null
    }

    // 多选时给列表底部留出 FloatingToolbar 的高度，避免最后一条被盖住。
    val toolbarInset by animateDpAsState(if (selecting) 64.dp else 0.dp, label = "historyToolbarInset")
    // 页面记录层的引用：多选工具栏在骨架之外，需要显式拿着这一层去采样（见 ErosSelectionToolbar）。
    var recordLayer by remember { mutableStateOf<LayerBackdrop?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        ErosPageScaffold(
            title = if (selecting) "已选 ${selection.size} 项" else "浏览历史",
            onBack = if (selecting) null else onBack,
            navigationIcon = navigationIcon,
            actions = {
                if (selecting) {
                    // 一个图标两种语义：点一次全选，再点一次清空勾选（与下载页一致）。
                    ErosBarIcon(
                        onClick = { selection.toggleAll(items.map { it.gid }) },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.SelectAll,
                            contentDescription = if (allSelected) "取消全选" else "全选",
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            floatingToolbar = {},
        ) { innerPadding, pageScrollBehavior, backdrop ->
            recordLayer = backdrop
            Box(
                Modifier
                    .fillMaxSize()
            ) {
                when {
                    loading -> HistorySkeleton(innerPadding = innerPadding, toolbarInset = toolbarInset)

                    items.isEmpty() -> ErosEmptyState(
                        title = "还没有浏览记录",
                        description = "打开画廊详情或开始阅读后，这里会出现记录",
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            // 与首页一致：滚动时大标题收进顶栏
                            .nestedScroll(pageScrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            start = ErosDimens.pagePadding,
                            end = ErosDimens.pagePadding,
                            top = innerPadding.calculateTopPadding() + ErosDimens.topContentGap,
                            bottom = ErosDimens.pagePadding + toolbarInset,
                        ),
                        verticalArrangement = Arrangement.spacedBy(ErosDimens.listGap),
                    ) {
                        itemsIndexed(items, key = { _, it -> it.gid }) { index, entry ->
                            val isSelected = selection.contains(entry.gid)
                            HistoryRow(
                                entry = entry,
                                index = index,
                                scrolling = listState.isScrollInProgress,
                                selecting = selecting,
                                selected = isSelected,
                                onClick = {
                                    if (selecting) toggle(entry.gid) else onOpen(entry)
                                },
                                onLongClick = {
                                    if (!selecting) selection.start(entry.gid) else toggle(entry.gid)
                                },
                                onDelete = {
                                    scope.launch {
                                        GalleryCache.deleteHistory(entry.gid)
                                        // 列表删了，卡片上的「已读完」徽标与续读页码也要一起清掉。
                                        ReadStore.clearProgress(entry.gid)
                                        reload()
                                    }
                                },
                                onRead = { onRead(entry) },
                            )
                        }
                    }
                }
            }
        }

        // 工具栏实现统一在 ErosSelectionToolbar（内部 ErosGlassBar 保证挂在记录层之外）。
        // 放在页面最外层 Box 里，而不是 Scaffold 的 floatingToolbar 槽位：
        // miuix Scaffold 的工具栏虽然画在内容之上，但命中测试会输给列表卡片（真机实测点不动）。
        if (selecting) {
            val enabled = selection.isNotEmpty()
            ErosSelectionToolbar(
                recordLayer = recordLayer,
                name = "history selection toolbar",
                enabled = enabled,
                showLabel = AppSettings.navBarMode != 1,
                onDelete = { confirming = true },
            )
        }

        // 删除确认：miuix 原生 OverlayDialog（与「图片缓存」一致），
        // 宿主由 App.kt 在外层补的 MiuixPopupUtils.MiuixPopupHost 提供。
        TrackOverlay(confirming)
        ErosDialog(
            backdrop = LocalPageBackdrop.current,
            show = confirming,
            title = "删除 ${selection.size} 条浏览记录？",
            summary = "只删除本机的浏览记录与封面缓存，不影响画廊本身。",
            onDismissRequest = { confirming = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { confirming = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(),
                ) { Text("取消", style = MiuixTheme.textStyles.button) }
                Button(
                    onClick = {
                        val gids = selection.selectedKeys()
                        confirming = false
                        exitSelection()
                        scope.launch {
                            gids.forEach {
                                runCatching { GalleryCache.deleteHistory(it) }
                                ReadStore.clearProgress(it)
                            }
                            reload()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary,
                        contentColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                ) { Text("删除", style = MiuixTheme.textStyles.button) }
            }
        }
    }
}

/**
 * 历史条目：与下载卡同款版式（左 120dp 封面 + 右侧标题 / 两行信息），
 * 只把「标签区」换成「续读位置 + 时间」、把评分行换成「删除」。
 *
 * 多选模式下右端悬浮出勾选框（不占布局槽位）、点击整卡切换选中，选中的卡片换成 miuix 选中灰底色。
 */
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    /** 在列表里的位置，用于入场错峰（与下载卡同一套动画）。 */
    index: Int = 0,
    scrolling: Boolean = false,
    selecting: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    /** 直接续读；`pages <= 0`（页码未知）时不渲染这个按钮。 */
    onRead: () -> Unit,
) {
    val entrance = rememberCardEntrance(entry.gid, index)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 与下载页卡片同高（下载卡也是 176dp：CARD_HEIGHT + insideMargin * 2）
            .height(CARD_HEIGHT + 16.dp)
            .cardEntrance(entrance.progress),
        insideMargin = PaddingValues(8.dp),
        // 选中态只改卡片底色（miuix 选中灰），不做按压反馈式的变暗。
        colors = if (selected) {
            CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh)
        } else {
            CardDefaults.defaultColors()
        },
        pressFeedbackType = if (scrolling) PressFeedbackType.None else PressFeedbackType.Sink,
        showIndication = !scrolling,
        holdDownState = false,
        onClick = onClick,
        onLongPress = onLongClick,
    ) {
        // 勾选框与删除图标都「悬浮」在卡片右端，不参与 Row 的测量：
        // 这样进入/退出多选、以及删除图标的出现与否，都不会挤动标题与时间/页数的位置。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CARD_HEIGHT),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CARD_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DownloadCardCover(
                thumb = entry.thumb,
                scrolling = scrolling,
                modifier = Modifier
                    .width(COVER_WIDTH)
                    .fillMaxHeight(),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Text(
                    text = GalleryTitles.primaryOrNull(entry.title, entry.titleJpn) ?: "画廊 ${entry.gid}",
                    style = MiuixTheme.textStyles.subtitle.copy(
                        fontSize = 14.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.weight(1f))
                // 与下载卡一致：底部两行信息 —— 上一行是时间，下一行是续读位置 + 删除
                Text(
                    text = historyTimeAgo(entry.updatedAt),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val readPage = (entry.page + 1).coerceIn(1, entry.pages.coerceAtLeast(1))
                    val percent = if (entry.pages > 0) {
                        ((readPage.toFloat() / entry.pages) * 100f).toInt().coerceIn(1, 100)
                    } else {
                        0
                    }
                    Text(
                        text = if (entry.pages > 0) {
                            "第 $readPage / ${entry.pages} 页 · $percent%"
                        } else {
                            "已打开"
                        },
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        // 右端浮层：默认态是删除图标，多选态是勾选框（带与卡片同色的底圈，压住正文也不影响可读性）。
        if (selecting) {
            val discColor = if (selected) {
                MiuixTheme.colorScheme.surfaceContainerHigh
            } else {
                MiuixTheme.colorScheme.surface
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .background(discColor, CircleShape)
                    .padding(2.dp),
            ) {
                Checkbox(
                    state = if (selected) ToggleableState.On else ToggleableState.Off,
                    onClick = null,
                )
            }
        } else {
            // 「阅读」+「删除」并排放在右下角：点卡片仍进详情，点这里直接续读。
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val canRead = entry.pages > 0
                if (canRead) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onRead)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Play,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "阅读",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = "删除",
                    tint = MiuixTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onDelete)
                        .padding(6.dp),
                )
            }
        }
        }
    }
}

/**
 * 首屏骨架：按可用高度铺满与真实行同高的占位卡。
 *
 * 历史页原来是「居中一行《正在加载…》」，大屏上会是一整片空白，所以这里复用
 * [HistoryRow] 的尺寸与列表 padding/gap，切到真实列表时不会跳行。
 */
@Composable
private fun HistorySkeleton(innerPadding: PaddingValues, toolbarInset: Dp) {
    val brush = rememberSkeletonBrush()
    val contentTop = innerPadding.calculateTopPadding() + ErosDimens.topContentGap
    val contentBottom = ErosDimens.pagePadding + toolbarInset
    val rowCount = skeletonRowCount(
        availableHeight = (rememberWindowHeightDp() - contentTop - contentBottom)
            .coerceAtLeast(1.dp),
        rowHeight = CARD_HEIGHT + 16.dp,
        rowSpacing = ErosDimens.listGap,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = ErosDimens.pagePadding,
                end = ErosDimens.pagePadding,
                top = contentTop,
                bottom = contentBottom,
            ),
        verticalArrangement = Arrangement.spacedBy(ErosDimens.listGap),
    ) {
        repeat(rowCount) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CARD_HEIGHT + 16.dp),
                insideMargin = PaddingValues(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CARD_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(COVER_WIDTH)
                            .fillMaxHeight()
                            .squircleClip(10.dp)
                            .background(brush),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SkeletonBar(14.dp, Modifier.fillMaxWidth(0.9f))
                        SkeletonBar(14.dp, Modifier.fillMaxWidth(0.6f))
                        Spacer(Modifier.weight(1f))
                        SkeletonBar(12.dp, Modifier.fillMaxWidth(0.45f))
                        SkeletonBar(12.dp, Modifier.fillMaxWidth(0.3f))
                    }
                }
            }
        }
    }
}

private fun historyTimeAgo(ts: Long): String {
    if (ts <= 0L) return ""
    val diff = currentTimeMillis() - ts
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000L} 天前"
        else -> {
            val days = diff / 86_400_000L
            "$days 天前"
        }
    }
}
