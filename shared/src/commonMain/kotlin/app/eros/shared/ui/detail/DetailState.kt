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

