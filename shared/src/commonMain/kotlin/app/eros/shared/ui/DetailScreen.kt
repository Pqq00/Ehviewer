// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

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
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/**
 * 详情页状态：详情、预览分页、收藏状态。
 *
 * 底栏由 App 层统一渲染（[ErosAppBottomBar]），这里只暴露状态与操作。
 */
class DetailState(
    val gid: Long,
    val token: String,
    private val api: EhApi = createEhApi(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 列表页带来的已知信息（标题/封面/页数/上传者），用于首屏预填。 */
    val seed: GalleryUi? = app.eros.shared.data.DetailSeedStore.get(gid, token)

    var detail by mutableStateOf<GalleryDetailUi?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** 失败的结构化原因；UI 用它决定"能不能点按重试"以及文案的语义。 */
    var lastError by mutableStateOf<EhError?>(null)
        private set
    var loadedOnce by mutableStateOf(false)
        private set

    /** 收藏状态与数量（App 层底栏读取）。 */
    var favorited by mutableStateOf(false)
        private set
    var favoriteCount by mutableStateOf(0)
        private set

    /** 收藏夹选择弹层。 */
    var favSheet by mutableStateOf(false)
        private set
    var favCatNames by mutableStateOf(List(10) { "收藏夹 $it" })
        private set
    var favCatCounts by mutableStateOf(List(10) { 0 })
        private set

    /** 当前画廊所在收藏夹下标（-1 表示未收藏 / 未知），用于弹层里打勾。 */
    var favSelected by mutableStateOf(-1)
        private set

    private var favCatsRequested = false

    var previews by mutableStateOf<List<PreviewThumbUi>>(emptyList())
        private set
    var previewPages by mutableStateOf(0)
        private set
    var loadingPreviews by mutableStateOf(false)
        private set
    var allPreviewsLoaded by mutableStateOf(false)
        private set
    private var loadedPreviewPage = 0

    fun ensureLoaded() {
        if (loadedOnce) return
        scope.launch { load() }
    }

    fun reload() {
        scope.launch {
            loadedOnce = false
            detail = null
            previews = emptyList()
            loadedPreviewPage = 0
            allPreviewsLoaded = false
            load(forceRefresh = true)
        }
    }

    /** P1-a: persist detail tags into the local tag table (namespace + translation). */
    private suspend fun cacheTags(d: GalleryDetailUi) {
        runCatching {
            val now = currentTimeMillis()
            val records = d.tags.flatMap { group ->
                group.tags.mapNotNull { raw ->
                    val name = raw.trim()
                    if (name.isEmpty()) {
                        null
                    } else {
                        val full = if (name.contains(':')) name else group.namespace + ":" + name
                        TagRecord(
                            namespace = group.namespace,
                            name = name.substringAfter(':', name),
                            full = full,
                            translated = com.hippo.ehviewer.client.EhTagDatabase.translate(name),
                            updatedAt = now,
                        )
                    }
                }
            }
            if (records.isNotEmpty()) GalleryCache.saveTags(records)
            GalleryCache.recordHistoryVisit(d.gid, token, d.pages)
        }.onFailure { AppLog.w("DetailState", "saveTags failed: " + it.message) }
    }

    private suspend fun load(forceRefresh: Boolean = false) {
        val t0 = currentTimeMillis()
        // P0：10 分钟内的内存缓存直接命中，完全不发请求。
        if (!forceRefresh && detail == null) {
            val mem = DetailMemoryCache.get(gid, token)
            if (mem != null && DetailMemoryCache.isFresh(gid, token, t0)) {
                detail = mem
                favorited = mem.isFavorited
                favoriteCount = mem.favoriteCount
                previews = mem.previews
                previewPages = mem.previewPages
                loadedPreviewPage = if (mem.previews.isNotEmpty()) 1 else 0
                allPreviewsLoaded = mem.previewPages <= 1
                loadedOnce = true
                loading = false
                AppLog.i("DetailState", "MEM-HIT gid=$gid cost=${currentTimeMillis() - t0}ms")
                return
            }
        }
        // P0：本地缓存先出内容（含评论/首屏预览），再走网络刷新。
        if (!forceRefresh && detail == null) {
            val cached = GalleryCache.detail(gid, token)
            if (cached != null) {
                detail = cached
                favorited = cached.isFavorited
                favoriteCount = cached.favoriteCount
                previews = cached.previews
                previewPages = cached.previewPages
                loadedPreviewPage = if (cached.previews.isNotEmpty()) 1 else 0
                allPreviewsLoaded = cached.previewPages <= 1
                loading = false
                DetailMemoryCache.put(cached, token)
                cacheTags(cached)
                AppLog.i("DetailState", "CACHE-HIT gid=$gid cost=${currentTimeMillis() - t0}ms")
            }
        }
        if (detail == null) loading = true
        error = null
        lastError = null
        val netStart = currentTimeMillis()
        val d = withEhError({ e ->
            lastError = e
            error = e.userMessage()
        }) {
            withContext(Dispatchers.IO) { api.detail(gid, token, forceRefresh = forceRefresh) }
        }
        val netMs = currentTimeMillis() - netStart
        if (d != null) {
            detail = d
            favorited = d.isFavorited
            favoriteCount = d.favoriteCount
            loadedOnce = true
            previews = d.previews
            previewPages = d.previewPages
            loadedPreviewPage = 1
            allPreviewsLoaded = d.previewPages <= 1
            GalleryCache.saveDetail(d, token)
            DetailMemoryCache.put(d, token)
            cacheTags(d)
            AppLog.i(
                "DetailState",
                "loaded gid=$gid net=${netMs}ms total=${currentTimeMillis() - t0}ms " +
                    "pages=${d.pages} comments=${d.comments.size} previews=${d.previews.size}/${d.previewPages}",
            )
        } else {
            AppLog.w("DetailState", "FAILED gid=$gid net=${netMs}ms kind=${lastError?.javaClass?.simpleName}: $error")
        }
        loading = false
    }

    fun loadMorePreviews() {
        if (loadingPreviews || loadedPreviewPage >= previewPages) return
        loadingPreviews = true
        scope.launch {
            try {
                val page = withContext(Dispatchers.IO) { api.previewPage(gid, token, loadedPreviewPage) }
                previews = (previews + page.thumbs).distinctBy { it.index }
                loadedPreviewPage++
                allPreviewsLoaded = loadedPreviewPage >= previewPages
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                AppLog.w("DetailState", "previews FAILED: ${t.message}")
            } finally {
                loadingPreviews = false
            }
        }
    }

    /**
     * 展示用缩略图：每行 3 张，未加载完时只展示 3 的倍数，
     * 余数留到下一批合并后再展示，避免最后一行缺格。
     */
    fun displayedPreviews(): List<PreviewThumbUi> {
        if (allPreviewsLoaded) return previews
        return previews.take(previews.size / 3 * 3)
    }

    /** 打开收藏夹选择：首次打开时顺带拉取收藏夹名称与计数。 */
    fun openFavSheet() {
        favSheet = true
        val name = detail?.favoriteName
        favSelected = if (name.isNullOrBlank()) {
            -1
        } else {
            val byName = favCatNames.indexOf(name)
            if (byName >= 0) byName else name.filter { it.isDigit() }.toIntOrNull() ?: -1
        }
        if (favCatsRequested) return
        favCatsRequested = true
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) { api.favourites(0) }
            }.getOrNull() ?: return@launch
            if (r.catNames.size >= 10) favCatNames = r.catNames.take(10)
            if (r.catCounts.size >= 10) favCatCounts = r.catCounts.take(10)
        }
    }

    fun closeFavSheet() {
        favSheet = false
    }

    /** 收藏到指定收藏夹（0..9）。 */
    fun favoriteTo(cat: Int) {
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { api.setFavourite(gid, token, cat) }
            }.getOrDefault(false)
            if (ok) {
                val was = favorited
                favorited = true
                favSelected = cat
                if (!was) favoriteCount += 1
                showSystemToast("已收藏到 " + favCatNames.getOrElse(cat) { "收藏夹 $cat" })
            } else {
                showSystemToast("收藏失败，请确认已登录")
            }
            favSheet = false
        }
    }

    /** 取消收藏。 */
    fun unfavorite() {
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { api.setFavourite(gid, token, -1) }
            }.getOrDefault(false)
            if (ok) {
                if (favorited) favoriteCount = (favoriteCount - 1).coerceAtLeast(0)
                favorited = false
                favSelected = -1
                showSystemToast("已取消收藏")
            } else {
                showSystemToast("取消收藏失败，请确认已登录")
            }
            favSheet = false
        }
    }
}

@Composable
fun DetailScreen(
    state: DetailState,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onReadAt: (Int) -> Unit,
    onOpenComments: () -> Unit,
    onSearchTag: (String) -> Unit,
    onSearchUploader: (String) -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp = 96.dp,
    onOpenGallery: ((GalleryRef) -> Unit)? = null,
) {
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
                            bottom = bottomInset + 16.dp,
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
@Composable
private fun languageLabel(raw: String?): String? = raw?.takeIf { it.isNotBlank() }?.let {
    when (it.lowercase()) {
        "chinese" -> "中文"
        "japanese" -> "日文"
        "english" -> "英文"
        "korean" -> "韩文"
        else -> it
    }
}

@Composable
private fun MetaCell(kind: MetaIconKind, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        MetaIcon(kind = kind, tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        Text(
            value,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 底行固定高度：徽章、上传者、评分垂直居中，避免不同卡片间位置漂移。 */
private val BottomRowHeight = 22.dp

/** 主标题行高（14sp/18sp）：用于按行数换算副标题可用行数。 */
private val MainTitleLineHeight = 18.sp

/** 评分槽固定宽度：星星与分数位数变化不影响其右边缘。 */
private val RatingSlotWidth = 108.dp

@Composable
private fun GalleryInfoSection(
    detail: GalleryDetailUi,
    favoriteCount: Int,
    onSearchTag: (String) -> Unit,
    onSearchUploader: (String) -> Unit,
) {
    val language = languageLabel(detail.language)
    val clipboard = LocalClipboardManager.current
    // 详情页同时显示两种语言的标题：主标题（5 行）+ 副标题（1 行）。
    val mainTitle = GalleryTitles.primary(detail.title, detail.titleJpn)
    val subTitle = GalleryTitles.secondary(detail.title, detail.titleJpn)
    fun copyTitle(text: String) {
        clipboard.setText(AnnotatedString(text))
        showSystemToast("标题已复制")
    }
    Column {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            insideMargin = PaddingValues(8.dp),
            cornerRadius = CardDefaults.CornerRadius,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(128.dp)
                            .height(173.dp)
                            .squircleClip(cornerRadius = 10.dp)
                            .background(MiuixTheme.colorScheme.surfaceVariant),
                    ) {
                        RemoteImage(
                            url = detail.thumb,
                            contentScale = ContentScale.Crop,
                            failureText = "无封面",
                            modifier = Modifier.coverBlur(scale = 0.8f),
                        )
                    }
                    // 右栏与封面等高：标题贴顶、六项信息贴底，信息行底部与封面底部对齐。
                    Column(
                        modifier = Modifier.weight(1f).height(173.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // 标题区占满右栏剩余高度：主标题优先用满（最多 5 行），余下的高度全部给副标题。
                        Column(modifier = Modifier.weight(1f)) {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                // 实测主标题行数，再按剩余高度换算副标题可显示的行数。
                                var mainLines by remember(mainTitle) { mutableIntStateOf(5) }
                                val subLines = (
                                    (maxHeight - 18.dp * mainLines) / 16.dp
                                    ).toInt().coerceIn(1, 8)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        mainTitle,
                                        style = MiuixTheme.textStyles.title4.copy(
                                            fontSize = 14.sp,
                                            lineHeight = MainTitleLineHeight,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        maxLines = 5,
                                        overflow = TextOverflow.Ellipsis,
                                        onTextLayout = { mainLines = it.lineCount },
                                        modifier = Modifier.clickable { copyTitle(mainTitle) },
                                    )
                                    subTitle?.let { title ->
                                        Text(
                                            title,
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                            maxLines = subLines,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable { copyTitle(title) },
                                        )
                                    }
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row {
                                MetaCell(MetaIconKind.Globe, language ?: "-", Modifier.weight(1f))
                                MetaCell(
                                    MetaIconKind.Disk,
                                    detail.size?.takeIf { it.isNotBlank() } ?: "-",
                                    Modifier.weight(1f),
                                )
                            }
                            Row {
                                MetaCell(
                                    MetaIconKind.Star,
                                    if (detail.ratingCount > 0) "${detail.ratingCount}" else "-",
                                    Modifier.weight(1f),
                                )
                                MetaCell(MetaIconKind.Heart, "$favoriteCount", Modifier.weight(1f))
                            }
                            Row {
                                MetaCell(MetaIconKind.Doc, "${detail.pages}", Modifier.weight(1f))
                                MetaCell(
                                    MetaIconKind.Clock,
                                    detail.posted?.takeIf { it.isNotBlank() }?.substringBefore(" ") ?: "-",
                                    Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                // 底行：高度固定，保证评分位置不随徽章/上传者长度漂移。
                Row(
                    modifier = Modifier.fillMaxWidth().height(BottomRowHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categoryName(detail.category)?.let {
                        Badge(
                            text = it,
                            containerColor = categoryColor(detail.category),
                            outerPadding = 0.dp,
                        )
                    }
                    detail.uploader?.takeIf { it.isNotBlank() }?.let { uploader ->
                        // 居中显示；clickable 只包文字，点击热区 = 文字本身。
                        Box(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                uploader,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { onSearchUploader(uploader) },
                            )
                        }
                    }
                    if (detail.uploader.isNullOrBlank()) Spacer(Modifier.weight(1f))
                    // 固定宽度 + 末端对齐：星星数量/分数位数变化都不会移动它。
                    Row(
                        modifier = Modifier.widthIn(min = RatingSlotWidth),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    ) {
                        if (detail.rating > 0f) {
                            RatingStars(
                                rating = detail.rating,
                                tint = Color(0xFFF6A821),
                                starSize = 12.dp,
                            )
                            Text(
                                String.format(Locale.US, "%.2f", detail.rating),
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}


/** 收藏夹选择弹层的一行。 */
@Composable
private fun FavCatRow(
    name: String,
    count: Int,
    selected: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = name,
            style = MiuixTheme.textStyles.body1,
            color = if (destructive) {
                MiuixTheme.colorScheme.error
            } else {
                MiuixTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count >= 0) {
            Text(
                text = count.toString(),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = "当前收藏夹",
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 标签：收起时展示前 8 个 chips + 内联「+N」，展开后全部 chips + 「收起」。 */
@Composable
private fun TagsSection(
    detail: GalleryDetailUi,
    onSearchTag: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        insideMargin = PaddingValues(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            detail.tags.forEach { group ->
                if (group.tags.isEmpty()) return@forEach
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GroupTitleChip(namespace = group.namespace)
                    group.tags.forEach { tag ->
                        TagChip(
                            tag = tag,
                            onClick = { onSearchTag("${group.namespace}:$tag") },
                            onLongClick = { selected = group.namespace to tag },
                        )
                    }
                }
            }
        }
    }

    val tagSelection = selected
    val raw = tagSelection?.let { "${it.first}:${it.second}" } ?: ""
    TrackOverlay(tagSelection != null)
    ErosBottomSheet(
        show = tagSelection != null,
        title = raw,
        onDismissRequest = { selected = null },
        onDismissFinished = { },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TagSheetAction("搜索这个标签") {
                selected = null
                onSearchTag(raw)
            }
            TagSheetAction("复制标签") {
                clipboard.setText(AnnotatedString(raw))
                selected = null
                showSystemToast("已复制")
            }
            TagSheetAction("加入监听") {
                selected = null
                scope.launch {
                    val ok = runCatching {
                        withContext(Dispatchers.IO) {
                            createEhApi().addWatchedTag(raw, watched = true, hidden = false)
                        }
                    }.isSuccess
                    showSystemToast(if (ok) "已加入监听" else "操作失败，请确认已登录")
                }
            }
            TagSheetAction("加入屏蔽") {
                AppSettings.filterRules =
                    AppSettings.filterRules + FilterRule(FilterMode.TAG, raw)
                selected = null
                showSystemToast("已加入屏蔽")
            }
        }
    }
}

private fun namespaceLabel(namespace: String): String = when (namespace) {
    "language" -> "语言"
    "parody" -> "作品"
    "character" -> "角色"
    "group" -> "团队"
    "artist" -> "画师"
    "circle" -> "社团"
    "cosplay" -> "角色扮演"
    "female" -> "女性"
    "male" -> "男性"
    "mixed" -> "混合"
    "other" -> "其他"
    "series" -> "系列"
    "event" -> "活动"
    "uploader" -> "上传者"
    "reclass" -> "重分类"
    "temp" -> "临时"
    else -> namespace
}

private fun namespaceColor(namespace: String): Color = when (namespace) {
    "language" -> Color(0xFFB26A00)
    "parody" -> Color(0xFF1565C0)
    "character" -> Color(0xFF6A1B9A)
    "female" -> Color(0xFFAD1457)
    "male" -> Color(0xFF283593)
    "mixed" -> Color(0xFF00695C)
    "other" -> Color(0xFF5D5D5D)
    "artist" -> Color(0xFF2E7D32)
    "circle" -> Color(0xFF6D4C41)
    "series" -> Color(0xFF0277BD)
    "event" -> Color(0xFFE65100)
    "uploader" -> Color(0xFF455A64)
    "cosplay" -> Color(0xFF7B1FA2)
    "group" -> Color(0xFF00838F)
    else -> Color(0xFF5D5D5D)
}

@Composable
private fun GroupTitleChip(
    namespace: String,
) {
    val color = namespaceColor(namespace)
    Text(
        text = namespaceLabel(namespace),
        style = MiuixTheme.textStyles.footnote2,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .squircleSurface(
                color = color.copy(alpha = 0.16f),
                cornerRadius = 8.dp,
            )
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
private fun TagChip(
    tag: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val revision = app.eros.shared.tags.TagDatabase.revision
    val translation = remember(tag, AppSettings.tagTranslation, revision) {
        if (AppSettings.tagTranslation) {
            com.hippo.ehviewer.client.EhTagDatabase.translate(tag)
        } else {
            null
        }
    }
    Text(
        text = translation ?: tag,
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.primary,
        maxLines = 1,
        modifier = modifier
            .squircleSurface(
                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.10f),
                cornerRadius = 8.dp,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
private fun TagSheetAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body1,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = null)
            .padding(vertical = 14.dp),
    )
}

/** 评论：Card 内前 3 条摘要 + 查看全部（卡片本身复用评论页的 [CommentCard]）。 */
@Composable
private fun CommentsSection(
    detail: GalleryDetailUi,
    onOpenComments: () -> Unit,
    onOpenGallery: ((GalleryRef) -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        insideMargin = PaddingValues(16.dp),
    ) {
        if (detail.comments.isEmpty()) {
            Text(
                "还没有评论",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                detail.comments.take(3).forEach {
                    CommentCard(comment = it, compact = true, onOpenGallery = onOpenGallery)
                }
                TextButton(
                    text = "查看全部 ${detail.comments.size} 条",
                    onClick = onOpenComments,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 缩略图单元。 */
@Composable
private fun ThumbCell(thumb: PreviewThumbUi, onClick: () -> Unit) {
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
