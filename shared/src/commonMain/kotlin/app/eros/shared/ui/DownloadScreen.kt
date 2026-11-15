// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eros.shared.data.GalleryTitles
import app.eros.shared.download.DownloadManager
import app.eros.shared.download.DownloadManifest
import app.eros.shared.download.DownloadStatus
import app.eros.shared.download.formatBytes
import app.eros.shared.download.formatClockTime
import app.eros.shared.download.isBusy
import app.eros.shared.settings.AppSettings
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 下载页：队列 + 进度 + 操作。 */
@Composable
fun DownloadScreen(
    onOpenDetail: (gid: Long, token: String, total: Int) -> Unit,
    onOpenReader: (gid: Long, token: String, total: Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAccount: () -> Unit,
    bottomInset: Dp = 0.dp,
) {
    LaunchedEffect(Unit) { DownloadManager.ensureInitialized() }
    val tasks by DownloadManager.tasks.collectAsState()
    val ready by DownloadManager.ready.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateMapOf<Long, Boolean>() }
    var confirming by remember { mutableStateOf(false) }
    val speeds = remember { mutableStateMapOf<Long, Long>() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val samples = mutableMapOf<Long, Pair<Long, Long>>()
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
            val running = DownloadManager.tasks.value.filter { it.status == DownloadStatus.RUNNING }
            val seen = running.map { it.gid }.toSet()
            samples.keys.retainAll(seen)
            speeds.keys.retainAll(seen)
            for (task in running) {
                val previous = samples[task.gid]
                val bytes = task.downloadedBytes
                if (previous != null && now > previous.second && bytes >= previous.first) {
                    val instant = (bytes - previous.first) * 1000.0 / (now - previous.second)
                    val smooth = if (speeds.containsKey(task.gid)) {
                        0.7 * speeds.getValue(task.gid) + 0.3 * instant
                    } else {
                        instant
                    }
                    speeds[task.gid] = smooth.toLong()

                }
                samples[task.gid] = bytes to now
            }
        }
    }

    val active = tasks.filter { it.status.isBusy || it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
    val done = tasks.filter { it.status == DownloadStatus.DONE }
    val showActive = if (tab == 2) emptyList() else active
    val showDone = if (tab == 1) emptyList() else done

    val scope = rememberCoroutineScope()
    val visibleTasks = showActive + showDone
    val allSelected = visibleTasks.isNotEmpty() && visibleTasks.all { selected.containsKey(it.gid) }
    val toolbarInset by animateDpAsState(if (selecting) 64.dp else 0.dp)

    fun exitSelection() {
        selecting = false
        selected.clear()
    }

    fun toggle(gid: Long) {
        if (selected.remove(gid) == null) selected[gid] = true
    }

    fun toggleAll() {
        if (allSelected) selected.clear() else visibleTasks.forEach { selected[it.gid] = true }
    }

    ErosPageScaffold(
        title = if (selecting) "已选 ${selected.size} 项" else "下载",
        navigationIcon = if (selecting) {
            {
                IconButton(onClick = { exitSelection() }) {
                    Icon(MiuixIcons.Close, contentDescription = "退出多选")
                }
            }
        } else {
            { AccountEntry(onOpenAccount) }
        },
        actions = {
            if (selecting) {
                IconButton(onClick = { toggleAll() }) {
                    Icon(
                        MiuixIcons.SelectAll,
                        contentDescription = if (allSelected) "取消全选" else "全选",
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            } else {
                HistoryAndSearchEntries(onOpenHistory, onOpenSearch)
            }
        },
        floatingToolbar = {},
    ) { innerPadding, pageScrollBehavior, pageBackdrop ->
        Box(
            Modifier
                .fillMaxSize()
                .then(if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier),
        ) {
            LazyColumn(
                state = listState,
                // 和首页/排行榜一致：把滚动喂给顶栏的 scrollBehavior，大标题才会随滚动收起。
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pageScrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = innerPadding.calculateTopPadding(),
                    bottom = bottomInset + 12.dp + toolbarInset,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "tabs") {
                    TabRow(
                        tabs = listOf(
                            "全部 ${tasks.size}",
                            "下载中 ${active.size}",
                            "已完成 ${done.size}",
                        ),
                        selectedTabIndex = tab,
                        onTabSelected = { tab = it },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                if (showActive.isNotEmpty()) {
                    item(key = "activeTitle") { SmallTitle(text = "下载中") }
                    items(showActive.size, key = { "active-${showActive[it].gid}" }) { index ->
                        val task = showActive[index]
                        DownloadTaskCard(
                            task = task,
                            speed = speeds[task.gid] ?: 0L,
                            index = index,
                            scrolling = listState.isScrollInProgress,
                            selecting = selecting,
                            selected = selected.containsKey(task.gid),
                            onClick = { if (selecting) toggle(task.gid) },
                            onOpenReader = { onOpenReader(task.gid, task.token, task.totalPages) },
                            onAction = {
                                when (task.status) {
                                    DownloadStatus.PAUSED -> DownloadManager.resume(task.gid)
                                    DownloadStatus.FAILED -> DownloadManager.retry(task.gid)
                                    else -> DownloadManager.pause(task.gid)
                                }
                            },
                            onLongClick = {
                                if (!selecting) {
                                    selecting = true
                                    selected[task.gid] = true
                                }
                            },
                        )
                    }
                }

                if (showDone.isNotEmpty()) {
                    item(key = "doneTitle") { SmallTitle(text = "已下载") }
                    items(showDone.size, key = { "done-${showDone[it].gid}" }) { index ->
                        val task = showDone[index]
                        DownloadTaskCard(
                            task = task,
                            speed = 0L,
                            index = index,
                            scrolling = listState.isScrollInProgress,
                            selecting = selecting,
                            selected = selected.containsKey(task.gid),
                            onClick = {
                                if (selecting) {
                                    toggle(task.gid)
                                } else {
                                    onOpenDetail(task.gid, task.token, task.totalPages)
                                }
                            },
                            onOpenReader = { onOpenReader(task.gid, task.token, task.totalPages) },
                            onAction = { onOpenReader(task.gid, task.token, task.totalPages) },
                            onLongClick = {
                                if (!selecting) {
                                    selecting = true
                                    selected[task.gid] = true
                                }
                            },
                        )
                    }
                }

                if (!ready) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "正在读取下载列表…",
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                } else if (showActive.isEmpty() && showDone.isEmpty()) {
                    item(key = "empty") {
                        DownloadEmptyState(
                            title = when (tab) {
                                1 -> "没有下载中的任务"
                                2 -> "还没有已完成的任务"
                                else -> "还没有下载"
                            },
                        )
                    }
                }
            }

            if (selecting) {
                val enabled = selected.isNotEmpty()
                // 多选态隐藏底部导航栏，工具栏与历史页一样贴屏幕底 24dp
                TrackOverlay()
                FloatingToolbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = enabled) { confirming = true }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            MiuixIcons.Delete,
                            contentDescription = "删除",
                            tint = if (enabled) {
                                MiuixTheme.colorScheme.error
                            } else {
                                MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            },
                            modifier = Modifier.size(22.dp),
                        )
                        if (AppSettings.navBarMode != 1) {
                            Text(
                                text = "删除",
                                style = MiuixTheme.textStyles.footnote2,
                                color = if (enabled) {
                                    MiuixTheme.colorScheme.error
                                } else {
                                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                },
                            )
                        }
                    }
                }
            }

            TrackOverlay(confirming)
            val pending = visibleTasks.filter { selected.containsKey(it.gid) }
            OverlayDialog(
                show = confirming,
                title = "删除 ${pending.size} 项下载？",
                summary = "将同时删除已下载的图片文件，不影响画廊本身。",
                onDismissRequest = { confirming = false },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { confirming = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消", style = MiuixTheme.textStyles.button)
                    }
                    Button(
                        onClick = {
                            confirming = false
                            val gids = pending.map { it.gid }
                            exitSelection()
                            scope.launch {
                                gids.forEach { runCatching { DownloadManager.remove(it, deleteFiles = true) } }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = MiuixTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("删除", style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadManifest,
    speed: Long,
    /** 在列表里的位置，用于入场错峰（与信息流下载卡同一套动画）。 */
    index: Int = 0,
    scrolling: Boolean = false,
    selecting: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onOpenReader: () -> Unit,
    onAction: () -> Unit,
    onLongClick: () -> Unit,
) {
    val entrance = rememberCardEntrance(task.gid, index)
    val discColor = if (selected) {
        MiuixTheme.colorScheme.surfaceContainerHigh
    } else {
        MiuixTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .cardEntrance(entrance.progress),
        insideMargin = PaddingValues(8.dp),
        colors = if (selected) {
            CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh)
        } else {
            CardDefaults.defaultColors()
        },
        pressFeedbackType = if (scrolling || selecting) PressFeedbackType.None else PressFeedbackType.Sink,
        showIndication = !scrolling && !selecting,
        onClick = onClick,
        onLongPress = onLongClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .fillMaxHeight()
                        .squircleClip(cornerRadius = 10.dp)
                        .background(MiuixTheme.colorScheme.surfaceVariant),
                ) {
                    if (task.thumb != null) {
                        AsyncImage(
                            model = task.thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().coverBlur(),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 12.dp, end = 0.dp),
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                        Text(
                            text = GalleryTitles.primary(task.title, task.titleJpn),
                            modifier = Modifier.align(Alignment.TopStart),
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).offset(x = (-6).dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (task.category >= 0) {
                                Badge(
                                    text = categoryName(task.category) ?: "Misc",
                                    containerColor = categoryColor(task.category),
                                )
                            }
                            val author = task.artist ?: task.uploader
                            if (author != null) {
                                Text(
                                    text = author,
                                    modifier = Modifier.weight(1f, fill = false),
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 38.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = metaText(task),
                            modifier = Modifier.weight(1f),
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (task.status != DownloadStatus.DONE) {
                            val right = rightText(task, speed)
                            if (right.isNotEmpty()) {
                                Text(
                                    text = right,
                                    fontSize = 11.sp,
                                    color = rightColor(task.status),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    if (task.status == DownloadStatus.DONE) {
                        // 完成时间单独一行，左边缘与「页数 · 大小」对齐
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = formatClockTime(task.finishedAt),
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            maxLines = 1,
                        )
                    }
                    if (task.status != DownloadStatus.DONE) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = task.progress.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                        )
                    }
                }
            }

            if (selecting) {
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
                Icon(
                    imageVector = if (task.status == DownloadStatus.DONE) {
                        MiuixIcons.Play
                    } else {
                        actionIcon(task.status)
                    },
                    contentDescription = if (task.status == DownloadStatus.DONE) {
                        "阅读"
                    } else {
                        actionLabel(task.status)
                    },
                    tint = if (task.status == DownloadStatus.FAILED) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp)
                        .clip(CircleShape)
                        .clickable(onClick = if (task.status == DownloadStatus.DONE) onOpenReader else onAction)
                        .padding(6.dp)
                        .size(24.dp),
                )
            }
        }
    }
}

private fun actionIcon(status: DownloadStatus) = when (status) {
    DownloadStatus.PAUSED -> MiuixIcons.Play
    DownloadStatus.FAILED -> MiuixIcons.Refresh
    DownloadStatus.DONE -> MiuixIcons.Play
    else -> MiuixIcons.Pause
}

private fun actionLabel(status: DownloadStatus) = when (status) {
    DownloadStatus.PAUSED -> "继续"
    DownloadStatus.FAILED -> "重试"
    DownloadStatus.DONE -> "打开"
    else -> "暂停"
}

private fun metaText(task: DownloadManifest): String {
    val pages = if (task.status == DownloadStatus.DONE) {
        "${task.totalPages} 页"
    } else {
        "${task.donePages}/${task.totalPages} 页"
    }
    return "$pages · ${formatBytes(task.downloadedBytes)}"
}

private fun rightText(task: DownloadManifest, speed: Long): String = when (task.status) {
    DownloadStatus.RUNNING -> if (speed > 0) "${formatBytes(speed)}/s" else "…"
    DownloadStatus.QUEUED -> "排队中"
    DownloadStatus.VERIFYING -> "校验中"
    DownloadStatus.COMMITTING -> "保存中"
    DownloadStatus.RETRY_WAIT -> "等待重试"
    DownloadStatus.PAUSED -> "已暂停"
    DownloadStatus.FAILED -> "失败"
    DownloadStatus.DONE -> formatClockTime(task.finishedAt)
}

@Composable
private fun rightColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.FAILED -> MiuixTheme.colorScheme.error
    DownloadStatus.PAUSED -> Color(0xFFEF6C00)
    else -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f)
}

@Composable
private fun DownloadEmptyState(title: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ErosEmptyState(
            title = title,
            description = "在画廊详情页点下载按钮开始",
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
    }
}
