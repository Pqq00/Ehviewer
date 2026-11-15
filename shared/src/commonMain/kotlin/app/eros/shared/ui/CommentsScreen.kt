// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eros.shared.data.CommentUi
import app.eros.shared.data.EhApi
import app.eros.shared.data.GalleryRef
import app.eros.shared.data.createEhApi
import app.eros.shared.data.toEhError
import app.eros.shared.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 评论页状态：加载全部评论、发表 / 编辑、投票。 */
class CommentsState(
    private val gid: Long,
    private val token: String,
    private val api: EhApi = createEhApi(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var comments by mutableStateOf<List<CommentUi>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var posting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var loadedOnce = false

    fun ensureLoaded() {
        if (loadedOnce) return
        loadedOnce = true
        scope.launch { load() }
    }

    fun reload() {
        scope.launch { load() }
    }

    private suspend fun load() {
        loading = true
        error = null
        try {
            val detail = withContext(Dispatchers.IO) { api.detail(gid, token, allComments = true) }
            comments = detail.comments
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // 解析层偶尔会抛非 EhError 的运行时异常（如空响应导致的 IndexOutOfBounds），
            // 以前会漏出去让 loading 永远停在 true、页面一直显示骨架。
            error = t.toEhError().userMessage()
        } finally {
            loading = false
        }
    }

    /** 发表评论；[editId] 非空时是编辑已有的评论。 */
    fun post(text: String, editId: Long? = null, onDone: () -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || posting) return
        scope.launch {
            posting = true
            error = null
            try {
                comments = withContext(Dispatchers.IO) {
                    api.postComment(gid, token, trimmed, editId)
                }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                error = t.toEhError().userMessage()
            } finally {
                posting = false
            }
        }
    }

    fun vote(comment: CommentUi, up: Boolean) {
        scope.launch {
            val target = if (up) 1 else -1
            runCatching {
                withContext(Dispatchers.IO) { api.voteComment(gid, token, comment.id, target) }
            }.onSuccess { newScore ->
                comments = comments.map { if (it.id == comment.id) it.copy(score = newScore) else it }
            }.onFailure {
                error = it.toEhError().userMessage()
            }
        }
    }
}

/**
 * 评论页：与其它页面同一套 `ErosPageScaffold`（大标题 + 栏体模糊）与下拉刷新，
 * 单条评论用共享的 [CommentCard]（详情页用它的 compact 形态）。
 */
@Composable
fun CommentsScreen(
    state: CommentsState,
    onBack: () -> Unit,
    onOpenGallery: ((GalleryRef) -> Unit)? = null,
) {
    LaunchedEffect(state) { state.ensureLoaded() }

    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<CommentUi?>(null) }
    val listState = rememberLazyListState()
    val inputFocus = remember { FocusRequester() }

    fun beginQuote(comment: CommentUi) {
        input = ">>${comment.id} $input"
        inputFocus.requestFocus()
    }

    ErosPageScaffold(title = "评论", onBack = onBack) { innerPadding, pageScrollBehavior, pageBackdrop ->
        Box(
            Modifier
                .fillMaxSize()
                .then(if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        state.loading && state.comments.isEmpty() -> CommentSkeleton(
                            modifier = Modifier.fillMaxSize(),
                            // 与真实列表用同一套内边距，骨架卡片的位置才不会跳。
                            contentTop = innerPadding.calculateTopPadding() + 8.dp,
                        )

                        state.comments.isEmpty() -> EmptyComments(
                            error = state.error,
                            onRetry = { state.reload() },
                        )

                        else -> {
                            val contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = innerPadding.calculateTopPadding() + 8.dp,
                                bottom = 8.dp,
                            )
                            PullToRefresh(
                                isRefreshing = state.loading,
                                onRefresh = { state.reload() },
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = contentPadding,
                                topAppBarScrollBehavior = pageScrollBehavior,
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .nestedScroll(pageScrollBehavior.nestedScrollConnection),
                                    contentPadding = contentPadding,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    item(key = "count") {
                                        Text(
                                            text = "共 ${state.comments.size} 条评论",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        )
                                    }
                                    items(state.comments, key = { it.id }) { comment ->
                                        val editAction: (() -> Unit)? = if (comment.editable) {
                                            { editing = comment; input = comment.text }
                                        } else {
                                            null
                                        }
                                        CommentCard(
                                            comment = comment,
                                            onVoteUp = { state.vote(comment, up = true) },
                                            onVoteDown = { state.vote(comment, up = false) },
                                            onQuote = { beginQuote(comment) },
                                            onEdit = editAction,
                                            onOpenGallery = onOpenGallery,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                state.error?.let { message ->
                    if (state.comments.isNotEmpty()) {
                        Text(
                            text = message,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }

                CommentInputBar(
                    input = input,
                    onInputChange = { input = it },
                    editing = editing,
                    onCancelEdit = { editing = null; input = "" },
                    posting = state.posting,
                    focusRequester = inputFocus,
                    onSend = {
                        state.post(input, editing?.id) {
                            input = ""
                            editing = null
                        }
                    },
                )
            }
        }
    }
}

/** 底部输入区：编辑态显示「正在编辑」与取消，键盘弹出时跟随上移。 */
@Composable
private fun CommentInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    editing: CommentUi?,
    onCancelEdit: () -> Unit,
    posting: Boolean,
    focusRequester: FocusRequester,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (editing != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "正在编辑这条评论",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "取消",
                    onClick = onCancelEdit,
                    minWidth = 48.dp,
                    minHeight = 28.dp,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = input,
                onValueChange = onInputChange,
                useLabelAsPlaceholder = true,
                label = if (editing != null) "编辑评论…" else "写评论…",
                minLines = if (editing != null) 2 else 1,
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            Button(
                onClick = onSend,
                enabled = input.isNotBlank() && !posting,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(
                    text = when {
                        posting -> "发送中"
                        editing != null -> "保存"
                        else -> "发送"
                    },
                )
            }
        }
    }
}

/** 空态 / 错误态。 */
@Composable
private fun EmptyComments(error: String?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.Notes,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = error ?: "还没有评论，来说点什么",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            if (error != null) {
                TextButton(text = "重试", onClick = onRetry)
            }
        }
    }
}

/** 评论骨架卡高度（含 insideMargin 12dp 与三行内容的估算值）。 */
private val CommentSkeletonCardHeight = 122.dp

/** 底部输入栏 + 系统手势区在骨架里预留的高度。 */
private val CommentInputBarReserve = 76.dp

/** 首屏骨架：按可用高度铺满色块占位卡，避免空白页 + 居中转圈。 */
@Composable
private fun CommentSkeleton(modifier: Modifier = Modifier, contentTop: Dp = 8.dp) {
    // 评论区没有「剩余高度」可取，只能按窗口高度反算：扣掉顶部留白、底部输入栏和标题行。
    val windowHeight = rememberWindowHeightDp()
    val cardCount = remember(windowHeight, contentTop) {
        skeletonRowCount(
            availableHeight = (windowHeight - contentTop - CommentInputBarReserve - 20.dp)
                .coerceAtLeast(1.dp),
            rowHeight = CommentSkeletonCardHeight,
            rowSpacing = 8.dp,
        )
    }
    Column(
        modifier = modifier
            .padding(start = 12.dp, end = 12.dp, top = contentTop),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 「共 N 条评论」占位：用一段透明文字撑出和真实列表完全相同的行高，块只负责闪烁。
        Box(contentAlignment = Alignment.CenterStart) {
            Text(
                text = "共 0 条评论",
                style = MiuixTheme.textStyles.footnote2,
                color = Color.Transparent,
            )
            SkeletonBlock(Modifier.width(72.dp).height(10.dp))
        }
        repeat(cardCount) {
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SkeletonBlock(Modifier.size(28.dp), RoundedCornerShape(50))
                        Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.CenterStart) {
                            SkeletonBlock(Modifier.width(96.dp).height(12.dp))
                        }
                        SkeletonBlock(Modifier.width(44.dp).height(12.dp))
                    }
                    SkeletonBlock(Modifier.fillMaxWidth().height(12.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.72f).height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBlock(Modifier.size(width = 70.dp, height = 22.dp), RoundedCornerShape(50))
                        Spacer(Modifier.weight(1f))
                        SkeletonBlock(Modifier.width(26.dp).height(12.dp))
                    }
                }
            }
        }
    }
}

/** 骨架占位块：浅色主题下 `surfaceVariant` 是纯白（与卡片同色），用 `secondary` 才不会隐形；带轻微呼吸动画。 */
@Composable
private fun SkeletonBlock(modifier: Modifier, shape: RoundedCornerShape = RoundedCornerShape(4.dp)) {
    val transition = rememberInfiniteTransition(label = "commentSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "commentSkeletonAlpha",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MiuixTheme.colorScheme.secondary.copy(alpha = alpha)),
    )
}
