// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.chrome

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.eros.shared.PlatformBackHandler
import app.eros.shared.data.GalleryLink
import app.eros.shared.data.GalleryRef
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.data.cache.SearchHistoryEntry
import app.eros.shared.data.cache.TagRecord
import app.eros.shared.settings.AppSettings
import app.eros.shared.ui.BarBlurStyle
import app.eros.shared.ui.CoverPrefetchEffect
import app.eros.shared.ui.DetailPrefetchEffect
import app.eros.shared.ui.ErosEmptyState
import app.eros.shared.ui.ErosErrorState
import app.eros.shared.ui.GalleryCard
import app.eros.shared.ui.GalleryGridSidePadding
import app.eros.shared.ui.SEARCH_HINTS
import app.eros.shared.ui.SearchState
import app.eros.shared.ui.barBlurColors
import app.eros.shared.ui.currentBarBlurStyle
import app.eros.shared.ui.design.GlassRailChrome
import app.eros.shared.ui.design.GlassRailStartInset
import app.eros.shared.ui.design.erosGlassRailStartInset
import app.eros.shared.ui.design.erosPageHorizontalInsets
import app.eros.shared.ui.effect.erosGlassBlur
import app.eros.shared.ui.effect.erosProgressiveGlassBlur
import app.eros.shared.ui.effect.isShaderSupported
import app.eros.shared.ui.rememberGalleryColumns
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationRailDefaults
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.glass.glass
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 搜索模式的弹簧缓动：把一秒的阻尼振动映射到 400ms 的入场动画上（对齐官方示例）。
 * 官方用自算的弹簧曲线而不是 tween 的贝塞尔，收放都能被 Cancel 反向打断。
 */
private val SearchModeEasing: Easing = run {
    val omega = 2.0 * PI / 0.75f
    val stiffness = (omega * omega).toFloat()
    val damping = (0.98f * 2.0 * omega).toFloat()
    val decay = -damping / 2f
    val frequency = sqrt(-(damping.toDouble() * damping - stiffness.toDouble() * 4)).toFloat() / 2f
    val sineCoefficient = decay / frequency
    Easing { fraction ->
        (
            1.0 + exp((decay * fraction).toDouble()) *
                (-cos((frequency * fraction).toDouble()) + sineCoefficient * sin((frequency * fraction).toDouble()))
            ).toFloat()
    }
}

/**
 * 搜索模式覆盖层（UI_LAYERS.md 的 L3 全屏浮层，方案 A 对齐官方 `GlassSearchOverlay`）。
 *
 * 与旧 `Route.Search` 页面的区别：搜索不再是导航目标，而是浮在整个窗口之上的模式——
 * - 页面/chrome 先按 [rememberGlassSearchBarAlpha] 淡出，浮层再从顶部滑入，Cancel 可反向打断；
 * - 因为宿主在根 `Scaffold` 内（`ErosAppChrome` 之后），它同时盖住侧栏与底栏；
 * - 返回键、Esc、点空白都会退出；IME 弹出时内容整体上移（`imePadding`）。
 *
 * 内容复用 [SearchState]：未搜索时给最近搜索与语法示例，搜索后是同一套 `GalleryCard` 网格与分页。
 */
@Composable
internal fun ErosSearchOverlay(
    expanded: Boolean,
    state: SearchState,
    onDismissRequest: () -> Unit,
    onGalleryClick: (GalleryUi) -> Unit,
    modifier: Modifier = Modifier,
    onOpenGallery: ((GalleryRef) -> Unit)? = null,
) {
    var suggestions by remember { mutableStateOf<List<TagRecord>>(emptyList()) }
    var recent by remember { mutableStateOf<List<SearchHistoryEntry>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    // 浮层在内容列里，底栏在搜索结果期间隐藏：只留系统导航栏的安全区。
    val navBottom = LocalAppBottomBarInset.current +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topInsetDp = with(LocalDensity.current) { WindowInsets.safeDrawing.getTop(this).toDp() }
    // 玻璃侧栏折叠时，胶囊浮在窗口顶部中央（页面靠顶栏给它留白）。搜索浮层没有顶栏，
    // 搜索框会正好被胶囊压住，所以这里按官方 `pageInsets.searchTop` 的做法补一段让位量：
    // 折叠时 = CollapsedTop + CollapsedHeight + 4dp，展开过程中线性收回到 0。
    val railInsetDp = GlassRailStartInset.current()
    val railProgress = if (GlassRailChrome.railMode) {
        with(LocalDensity.current) {
            (railInsetDp / (GlassNavigationRailDefaults.Margin + GlassNavigationRailDefaults.ExpandedWidth))
                .coerceIn(0f, 1f)
        }
    } else {
        1f
    }
    val capsuleClearance = if (GlassRailChrome.railMode) {
        (GlassNavigationRailDefaults.CollapsedTop + GlassNavigationRailDefaults.CollapsedHeight + 4.dp) *
            (1f - railProgress)
    } else {
        0.dp
    }
    // 展开的面板是带圆角与阴影的悬浮块：内容若正好从面板右缘开始，圆角会压掉输入框
    // 左端与首列文字的开头。页面靠自身 12dp 横向 padding 避开，搜索浮层是贴边布局，
    // 所以这里把面板让位量再加出同样的一段间隙。安全区（横屏挖孔）另外由
    // erosPageHorizontalInsets() 补齐，与页面内容列保持一致。
    val railGap = 12.dp * railProgress
    // 搜索栏高 44dp + 上下各 4dp 内边距，再加状态栏安全区与胶囊让位，就是列表要让出的高度。
    val contentTopPadding = topInsetDp + capsuleClearance + 52.dp
    // 站点 Profile 切换后缓存被清空，屏上结果一起换掉。
    val profileRevision = AppSettings.profileRevision
    var seenProfileRevision by remember { mutableIntStateOf(profileRevision) }
    LaunchedEffect(profileRevision) {
        if (profileRevision != seenProfileRevision) {
            seenProfileRevision = profileRevision
            state.refresh()
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    // 上游把 TextFieldState 私有化，我们的搜索是「输入完再提交」，所以用 vendored 版本共享这段状态。
    val queryState = rememberTextFieldState()
    fun setQueryText(value: String) {
        queryState.edit {
            replace(0, length, value)
            placeCursorAtEnd()
        }
    }
    // 从标签/上传者等外部入口进来时，把关键词回填到输入框。
    // 上游浮层在展开时会先 clearText()，所以等一帧再写，避免被它清掉。
    LaunchedEffect(expanded, state.query) {
        if (expanded && state.query.isNotBlank() && queryState.text.toString() != state.query) {
            withFrameNanos { }
            setQueryText(state.query)
        }
    }
    val submit: (String) -> Unit = { text ->
        val q = text.trim()
        val ref = GalleryLink.first(q)
        when {
            // 粘贴画廊链接时直接进详情，不再当关键词搜（与旧搜索页一致）。
            ref != null && onOpenGallery != null -> {
                onOpenGallery.invoke(ref)
                onDismissRequest()
            }
            q.isNotBlank() -> {
                setQueryText(q)
                state.search(q)
            }
        }
        keyboard?.hide()
    }

    ErosGlassSearchOverlay(
        expanded = expanded,
        queryState = queryState,
        onDismissRequest = onDismissRequest,
        scrollMaterialThreshold = 16.dp,
        modifier = modifier,
        // 搜索框让开玻璃侧栏，和页面内容列对齐；展开的面板是带圆角的悬浮块，
        // 这里额外留 [railGap]，否则输入框左端会被面板圆角压掉。
        horizontalInsetsModifier = Modifier
            .erosPageHorizontalInsets()
            .erosGlassRailStartInset(gap = railGap),
        label = "搜索标签或关键词",
        // 结果滚过一点距离后，搜索框与 Cancel 才淡入玻璃；未搜索时列表没有滚动量。
        scrollOffset = {
            when {
                !state.searched -> 0f
                gridState.firstVisibleItemIndex > 0 -> Float.MAX_VALUE
                else -> gridState.firstVisibleItemScrollOffset.toFloat()
            }
        },
        searchTopPadding = { capsuleClearance },
        blurEnabled = AppSettings.barBlurEnabled,
    ) { query ->
        LaunchedEffect(expanded) {
            if (expanded) {
                recent = runCatching { GalleryCache.searchHistory(8) }.getOrDefault(emptyList())
            } else {
                suggestions = emptyList()
            }
        }
        LaunchedEffect(query, expanded) {
            val q = query.trim()
            if (!expanded || q.length < 2 || q == state.query) {
                suggestions = emptyList()
                return@LaunchedEffect
            }
            delay(180)
            suggestions = runCatching { GalleryCache.searchTags(q, 10) }.getOrDefault(emptyList())
        }
        Box(
            Modifier
                .fillMaxSize()
                .erosPageHorizontalInsets()
                .erosGlassRailStartInset(gap = railGap),
        ) {
            SearchOverlayBody(
                state = state,
                input = query,
                suggestions = suggestions,
                recent = recent,
                navBottom = navBottom,
                gridState = gridState,
                topPadding = contentTopPadding,
                onPick = submit,
                onClearHistory = {
                    scope.launch {
                        GalleryCache.clearSearchHistory()
                        recent = emptyList()
                    }
                },
                onGalleryClick = onGalleryClick,
            )
        }
    }
}

/** 未搜索：最近搜索 + 常用标签；已搜索：结果网格（与列表页同一套 [GalleryCard]）。 */
@Composable
private fun SearchOverlayBody(
    state: SearchState,
    input: String,
    suggestions: List<TagRecord>,
    recent: List<SearchHistoryEntry>,
    navBottom: androidx.compose.ui.unit.Dp,
    gridState: LazyGridState,
    topPadding: Dp,
    onPick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onGalleryClick: (GalleryUi) -> Unit,
) {
    CoverPrefetchEffect(gridState, state.items)
    DetailPrefetchEffect(gridState, state.items)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gridPadding = PaddingValues(
            start = GalleryGridSidePadding,
            end = GalleryGridSidePadding,
            // 顶部让出搜索栏（含状态栏安全区），内容滚动时才从栏下穿过。
            top = topPadding + 12.dp,
            bottom = navBottom + 12.dp,
        )
        val columns = if (AppSettings.cardStyle == "download") {
            1
        } else {
            rememberGalleryColumns(maxWidth - GalleryGridSidePadding * 2)
        }
        val nearBottom by remember {
            derivedStateOf {
                val info = gridState.layoutInfo
                // 头部是「搜索框提交 / 建议 / 最近搜索」这类整行项，卡片从 headerCount 之后开始。
                val headerCount = if (!state.searched || suggestions.isNotEmpty() ||
                    (input.isNotBlank() && input.trim() != state.query)
                ) {
                    1
                } else {
                    0
                }
                val lastIndex = headerCount + state.items.size - 1
                state.items.isNotEmpty() && !state.loadingMore &&
                    info.visibleItemsInfo.any { it.index >= lastIndex }
            }
        }
        LaunchedEffect(nearBottom, state.items.size) {
            if (nearBottom) state.loadMore()
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = gridPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 头部条：未搜索时给建议/最近/示例；已搜索但输入框改过词时只保留显式提交行。
            if (!state.searched || suggestions.isNotEmpty() ||
                (input.isNotBlank() && input.trim() != state.query)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "hints") {
                    Column(Modifier.fillMaxWidth()) {
                        // 顶部给一行显式提交：不依赖软键盘的回车/搜索键。
                        if (input.isNotBlank() && input.trim() != state.query) {
                            Text(
                                "搜索「" + input.trim() + "」",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(input) }
                                    .padding(vertical = 10.dp),
                            )
                        }
                        if (suggestions.isNotEmpty()) {
                            suggestions.forEach { tag ->
                                val label = tag.translated ?: tag.name
                                Text(
                                    label + "  ·  " + tag.full + "  ·  " + tag.count,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val term = if (tag.name.contains(' ')) {
                                                tag.namespace + ":\"" + tag.name + "\""
                                            } else {
                                                tag.full
                                            }
                                            onPick(term)
                                        }
                                        .padding(vertical = 10.dp),
                                )
                            }
                            Box(Modifier.height(8.dp))
                        } else {
                            if (recent.isNotEmpty()) {
                                Text("最近搜索", style = MiuixTheme.textStyles.subtitle)
                                recent.forEach { entry ->
                                    Text(
                                        entry.query,
                                        style = MiuixTheme.textStyles.body2,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onPick(entry.query) }
                                            .padding(vertical = 9.dp),
                                    )
                                }
                                Text(
                                    "清空最近搜索",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { onClearHistory() }
                                        .padding(vertical = 8.dp),
                                )
                                Box(Modifier.height(18.dp))
                            }
                            Text("语法示例", style = MiuixTheme.textStyles.subtitle)
                            SEARCH_HINTS.forEach { hint ->
                                Text(
                                    hint,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(hint) }
                                        .padding(vertical = 8.dp),
                                )
                            }
                            Text(
                                "支持 namespace:tag、多个词空格分隔、-tag 排除；含空格的标签用引号",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            if (state.searched) {
                itemsIndexed(state.items, key = { _, it -> it.gid }) { index, gallery ->
                    GalleryCard(
                        gallery = gallery,
                        index = index,
                        scrolling = gridState.isScrollInProgress,
                        onClick = { onGalleryClick(gallery) },
                    )
                }
                if (state.loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "loadingMore") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                val footerError = state.error
                if (footerError != null && state.items.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "footerError") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "加载失败，点按重试",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.clickable { state.retry() },
                            )
                        }
                    }
                }
                if (state.endReached) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "endReached") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
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

        // 首屏加载：结果为空时居中转圈，不让搜索框下面的空白区域看起来像卡住。
        if (state.loading && state.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(top = 56.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        // 错误 / 空结果：网格里本来没有卡片，覆盖层居中显示，避免懒加载项拿到无限高度约束。
        if (state.searched && state.items.isEmpty() && !state.loading) {
            Box(Modifier.fillMaxSize().padding(top = 56.dp)) {
                val blockingError = state.error
                if (blockingError != null) {
                    ErosErrorState(message = blockingError, onRetry = { state.retry() })
                } else {
                    ErosEmptyState(
                        title = "没有找到结果",
                        description = "换一组关键词，或检查搜索语法（支持 namespace:tag、-tag 排除）",
                    )
                }
            }
        }
    }
}
