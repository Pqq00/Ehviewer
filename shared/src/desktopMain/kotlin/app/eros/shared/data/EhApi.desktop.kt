// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.net.EhCalls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 在 IO 线程跑阻塞请求，并把协程取消接到 OkHttp（与 Android 端同一套做法）。
 * 详见 `EhApi.android.kt` 里同名函数的注释。
 */
internal suspend fun <T> onIoBlocking(block: (EhCalls.Handle) -> T): T =
    withContext(Dispatchers.IO) {
        val handle = EhCalls.newHandle()
        val watchdog = launch {
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                EhCalls.cancel(handle)
                throw e
            }
        }
        try {
            block(handle)
        } finally {
            watchdog.cancel()
            EhCalls.detach(handle)
        }
    }

/**
 * 唯一的数据入口：全部走 :core 的 [EhEngine]。
 * 之前这里还有一层 EhApi，现在合并掉了，避免两套实现并存。
 */
private class EhApiImpl : EhApi {

    override suspend fun frontPage(
        nextHref: String?,
        cats: Int?,
        forceRefresh: Boolean,
    ): GalleryPage = onIoBlocking { handle ->
        val page = EhEngine.getGalleryList(
            nextHref = nextHref,
            fCats = cats?.let { (com.hippo.ehviewer.client.EhConfig.ALL_CATEGORY and it.inv()) or 0 },
            forceRefresh = forceRefresh,
            cancelKey = handle,
        )
        GalleryPage(
            items = page.items.map { gi ->
                GalleryUi(
                    gid = gi.gid,
                    token = gi.token.orEmpty(),
                    title = gi.title ?: "(untitled)",
                    titleJpn = gi.titleJpn?.takeIf { it.isNotBlank() },
                    thumb = gi.thumb,
                    category = gi.category,
                    tags = gi.simpleTags?.toList() ?: emptyList(),
                    uploader = gi.uploader,
                    pages = gi.pages,
                    rating = gi.rating.takeIf { it > 0f } ?: -1f,
                    posted = gi.posted,
                )
            },
            nextHref = page.nextHref,
        )
    }

    override suspend fun popular(
        nextHref: String?,
        forceRefresh: Boolean,
    ): GalleryPage = onIoBlocking { handle ->
        val page = EhEngine.getPopularList(nextHref = nextHref, forceRefresh = forceRefresh, cancelKey = handle)
        GalleryPage(
            items = page.items.map { gi ->
                GalleryUi(
                    gid = gi.gid,
                    token = gi.token.orEmpty(),
                    title = gi.title ?: "(untitled)",
                    titleJpn = gi.titleJpn?.takeIf { it.isNotBlank() },
                    thumb = gi.thumb,
                    category = gi.category,
                    tags = gi.simpleTags?.toList() ?: emptyList(),
                    uploader = gi.uploader,
                    pages = gi.pages,
                    rating = gi.rating.takeIf { it > 0f } ?: -1f,
                    posted = gi.posted,
                )
            },
            nextHref = page.nextHref,
        )
    }

    override suspend fun detail(
        gid: Long,
        token: String,
        allComments: Boolean,
        forceRefresh: Boolean,
    ): GalleryDetailUi =
        onIoBlocking { handle ->
            val d = EhEngine.getGalleryDetail(gid, token, allComments, forceRefresh, cancelKey = handle)
            d.toUi()
        }

    override suspend fun postComment(
        gid: Long,
        token: String,
        text: String,
        editId: Long?,
    ): List<CommentUi> = withContext(Dispatchers.IO) {
        EhEngine.commentGallery(gid, token, text, editId).comments.map { it.toUi() }
    }

    override suspend fun voteComment(
        gid: Long,
        token: String,
        commentId: Long,
        vote: Int,
    ): Int = withContext(Dispatchers.IO) {
        val detail = EhEngine.getGalleryDetail(gid, token)
        val apiUid = detail.apiUid
        val apiKey = detail.apiKey
        if (apiUid <= 0 || apiKey.isNullOrBlank()) throw IllegalStateException("缺少 API 凭据，无法投票")
        EhEngine.voteComment(apiUid, apiKey, gid, token, commentId, vote).score
    }

    override suspend fun previewPage(gid: Long, token: String, page: Int): PreviewPageUi =
        onIoBlocking { handle ->
            val result = EhEngine.getPreviewPage(gid, token, page, cancelKey = handle)
            PreviewPageUi(
                thumbs = result.previews.map { p ->
                    PreviewThumbUi(
                        index = p.position,
                        thumbUrl = p.imageUrl.orEmpty(),
                        pageUrl = p.pageUrl.orEmpty(),
                        // 预览图是雪碧图，第 2 页起同样要靠 offset/clip 切图，漏了每格都会画整张拼图。
                        offsetX = p.offsetX.coerceAtLeast(0),
                        offsetY = p.offsetY.coerceAtLeast(0),
                        clipWidth = p.clipWidth.coerceAtLeast(0),
                        clipHeight = p.clipHeight.coerceAtLeast(0),
                    )
                },
                totalPreviewPages = result.totalPreviewPages,
            )
        }

    override suspend fun search(
        query: String,
        nextHref: String?,
        catsExclude: Int?,
        forceRefresh: Boolean,
    ): GalleryPage =
        onIoBlocking { handle ->
            val page = EhEngine.searchGalleryList(query, catsExclude, nextHref, forceRefresh = forceRefresh, cancelKey = handle)
            GalleryPage(
                items = page.items.map { gi ->
                    GalleryUi(
                        gid = gi.gid,
                        token = gi.token.orEmpty(),
                        title = gi.title ?: "(untitled)",
                        titleJpn = gi.titleJpn?.takeIf { it.isNotBlank() },
                        thumb = gi.thumb,
                        category = gi.category,
                        tags = gi.simpleTags?.toList() ?: emptyList(),
                        uploader = gi.uploader,
                        pages = gi.pages,
                        rating = gi.rating.takeIf { it > 0f } ?: -1f,
                        posted = gi.posted,
                    )
                },
                nextHref = page.nextHref,
            )
        }
    override suspend fun searchByUploader(uploader: String, nextHref: String?): GalleryPage =
        onIoBlocking { handle ->
            val page = EhEngine.searchUploaderList(uploader, nextHref, cancelKey = handle)
            GalleryPage(
                items = page.items.map { gi ->
                    GalleryUi(
                        gid = gi.gid,
                        token = gi.token.orEmpty(),
                        title = gi.title ?: "(untitled)",
                        titleJpn = gi.titleJpn?.takeIf { it.isNotBlank() },
                        thumb = gi.thumb,
                        category = gi.category,
                        tags = gi.simpleTags?.toList() ?: emptyList(),
                        uploader = gi.uploader,
                        pages = gi.pages,
                        rating = gi.rating.takeIf { it > 0f } ?: -1f,
                        posted = gi.posted,
                    )
                },
                nextHref = page.nextHref,
            )
        }
    override suspend fun readerPageUrls(gid: Long, token: String): List<String> =
        withContext(Dispatchers.IO) {
            val map = PageBatches.fetch(gid, token, 0, 0)
            map.entries.sortedBy { it.key }.map { it.value }
                .ifEmpty { EhEngine.getReaderPageUrls(gid, token) }
        }
    override suspend fun fetchTagDatabase(onProgress: (Float) -> Unit): String =
        withContext(Dispatchers.IO) { com.hippo.ehviewer.client.EhEngine.fetchTagDatabase { p -> onProgress(p) } }

    override suspend fun fetchTagDatabaseIfChanged(
        knownStamp: String?,
        onProgress: (Float) -> Unit,
    ): TagDatabaseFetch = withContext(Dispatchers.IO) {
        val engine = com.hippo.ehviewer.client.EhEngine
        val stamp = engine.fetchTagDatabaseStamp()
        if (stamp != null && stamp == knownStamp) return@withContext TagDatabaseFetch(null, stamp)
        val json = engine.fetchTagDatabase { p -> onProgress(p) }
        TagDatabaseFetch(json, stamp)
    }

    override suspend fun imageBytes(imageUrl: String, onProgress: (Float) -> Unit): ByteArray =
        withContext(Dispatchers.IO) {
            com.hippo.ehviewer.client.EhEngine.downloadImageWithProgress(imageUrl) { p -> onProgress(p) }
        }

    override suspend fun readerPage(pageUrl: String): ReaderPageUi =
        onIoBlocking { handle ->
            val page = EhEngine.getReaderPage(pageUrl, handle)
            ReaderPageUi(page.imageUrl)
        }

    override suspend fun readerPageUrl(pageUrl: String): String =
        onIoBlocking { handle -> EhEngine.getReaderPage(pageUrl, handle).imageUrl }

    override suspend fun readerImage(pageUrl: String, onProgress: (Float) -> Unit): ReaderImageUi =
        withContext(Dispatchers.IO) {
            val image = EhEngine.readerImage(pageUrl) { p -> onProgress(p) }
            ReaderImageUi(image.bytes, image.imageUrl)
        }

    override suspend fun favourites(favCat: Int, nextHref: String?): FavouritesUi =
        onIoBlocking { handle ->
            val result = EhEngine.getFavourites(favCat, nextHref, handle)
            FavouritesUi(
                items = result.galleryInfoList.orEmpty().map { gi ->
                    GalleryUi(
                        gid = gi.gid,
                        token = gi.token.orEmpty(),
                        title = gi.title ?: "(untitled)",
                        titleJpn = gi.titleJpn?.takeIf { it.isNotBlank() },
                        thumb = gi.thumb,
                        category = gi.category,
                        tags = gi.simpleTags?.toList() ?: emptyList(),
                        uploader = gi.uploader,
                        pages = gi.pages,
                        rating = gi.rating.takeIf { it > 0f } ?: -1f,
                        posted = gi.posted,
                    )
                },
                catNames = result.catArray?.map { it ?: "" }.orEmpty(),
                catCounts = result.countArray?.toList().orEmpty(),
                nextHref = result.nextHref?.takeIf { it.isNotBlank() },
            )
        }

    override suspend fun setFavourite(gid: Long, token: String, dstCat: Int): Boolean =
        withContext(Dispatchers.IO) { EhEngine.addFavorites(gid, token, dstCat) }

    override suspend fun watchedTags(): List<WatchedTagUi> = withContext(Dispatchers.IO) {
        EhEngine.getWatchedList().map { u ->
            WatchedTagUi(
                tag = u.tagName.orEmpty(),
                watched = u.watched,
                hidden = u.hidden,
                weight = u.tagWeight,
                id = u.userTagId.orEmpty(),
            )
        }.filter { it.tag.isNotBlank() }
    }

    override suspend fun addWatchedTag(
        tag: String,
        watched: Boolean,
        hidden: Boolean,
    ): List<WatchedTagUi> = withContext(Dispatchers.IO) {
        EhEngine.addWatchedTag(tag, watched, hidden).toUi()
    }

    override suspend fun deleteWatchedTag(userTagId: String, weight: Int): List<WatchedTagUi> =
        withContext(Dispatchers.IO) {
            EhEngine.deleteWatchedTag(userTagId, weight).toUi()
        }

    override suspend fun topList(): List<TopPeriodUi> = onIoBlocking { handle ->
        val detail = EhEngine.getTopListPage(handle)
        val info = detail.galleryTopListInfo
        // TopListInfo.get(i): 0=昨日 1=本月 2=今年 3=全部
        val labels = listOf("今日", "本月", "今年", "全部")
        labels.mapIndexed { index, label ->
            val arr = info?.get(index)
            val items = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                val item = arr?.get(i) ?: return@mapNotNull null
                val href = item.href ?: return@mapNotNull null
                val match = Regex("""/(?:g|mpv)/(\d+)/([0-9a-fA-F]+)""").find(href) ?: return@mapNotNull null
                TopGalleryUi(
                    rank = i + 1,
                    title = item.value ?: href,
                    gid = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null,
                    token = match.groupValues[2],
                )
            }
            TopPeriodUi(label, items)
        }
    }
    override suspend fun topRated(nextHref: String?, minRating: Int): GalleryPage =
        onIoBlocking { handle ->
            val page = EhEngine.searchGalleryList(
                query = "",
                nextHref = nextHref,
                minRating = minRating,
                cancelKey = handle,
            )
            GalleryPage(
                items = page.items.map { gi ->
                    GalleryUi(
                        gid = gi.gid,
                        token = gi.token.orEmpty(),
                        title = gi.title ?: "(untitled)",
                        titleJpn = gi.titleJpn?.takeIf { it.isNotBlank() },
                        thumb = gi.thumb,
                        category = gi.category,
                        tags = gi.simpleTags?.toList() ?: emptyList(),
                        uploader = gi.uploader,
                        pages = gi.pages,
                        rating = gi.rating.takeIf { it > 0f } ?: -1f,
                        posted = gi.posted,
                    )
                },
                nextHref = page.nextHref,
            )
        }
    override suspend fun rankList(
        tl: Int,
        page: Int,
        forceRefresh: Boolean,
        enrichTags: Boolean,
    ): RankPageUi = onIoBlocking { handle ->
        val result = EhEngine.getRankList(tl, page, forceRefresh, enrichTags, handle)
        RankPageUi(
            items = result.items.map { gi ->
                GalleryUi(
                    gid = gi.gid,
                    token = gi.token.orEmpty(),
                    title = gi.title ?: "(untitled)",
                    thumb = gi.thumb,
                    category = gi.category,
                    tags = gi.simpleTags?.toList().orEmpty(),
                    uploader = gi.uploader,
                    pages = gi.pages,
                    rating = gi.rating.takeIf { it > 0f } ?: -1f,
                    posted = gi.posted,
                )
            },
            nextPage = result.nextPage,
            totalPages = result.totalPages,
        )
    }
    override suspend fun enrichGalleries(items: List<GalleryUi>): List<GalleryUi> =
        onIoBlocking { key ->
            if (items.isEmpty()) return@onIoBlocking emptyList()
            val infos = items.map { ui ->
                com.hippo.ehviewer.client.data.GalleryInfo().apply {
                    gid = ui.gid
                    token = ui.token
                }
            }
            EhEngine.fillGalleryListByApiBestEffort(infos, key)
            val done = infos.filter { it.title != null }
            if (done.isEmpty()) return@onIoBlocking emptyList()
            items.mapNotNull { ui ->
                val gi = done.firstOrNull { it.gid == ui.gid } ?: return@mapNotNull null
                ui.copy(
                    title = gi.title ?: ui.title,
                    thumb = gi.thumb ?: ui.thumb,
                    category = gi.category,
                    tags = gi.simpleTags?.toList().orEmpty(),
                    uploader = gi.uploader ?: ui.uploader,
                    pages = if (gi.pages > 0) gi.pages else ui.pages,
                    rating = if (gi.rating > 0f) gi.rating else ui.rating,
                    posted = gi.posted ?: ui.posted,
                )
            }
        }

    override fun isSignedIn(): Boolean = com.hippo.ehviewer.net.EhAuth.hasSignedIn()

    override suspend fun siteProfiles(): List<SiteProfileUi> = withContext(Dispatchers.IO) {
        EhEngine.fetchSiteProfiles().map { SiteProfileUi(number = it.number, name = it.name, selected = it.selected) }
    }

    override fun selectSiteProfile(number: Int) {
        EhEngine.selectSiteProfile(number)
    }
}

actual fun createEhApi(): EhApi = EhApiImpl()
private fun List<com.hippo.ehviewer.client.data.UserTag>.toUi(): List<WatchedTagUi> =
    map { u ->
        WatchedTagUi(
            tag = u.tagName.orEmpty(),
            watched = u.watched,
            hidden = u.hidden,
            weight = u.tagWeight,
            id = u.userTagId.orEmpty(),
        )
    }.filter { it.tag.isNotBlank() }
/** GalleryDetail → UI 模型。 */
private fun com.hippo.ehviewer.client.data.GalleryDetail.toUi(): GalleryDetailUi = GalleryDetailUi(
    gid = gid,
    title = title ?: "(untitled)",
    titleJpn = titleJpn?.takeIf { it.isNotBlank() },
    thumb = thumb,
    category = category,
    uploader = uploader,
    posted = posted,
    pages = pages,
    rating = rating,
    favoriteCount = favoriteCount,
    isFavorited = isFavorited,
    favoriteName = favoriteName,
    language = language,
    size = size,
    tags = tags?.map { group ->
        TagGroupUi(
            namespace = group.groupName.orEmpty(),
            tags = (0 until group.size()).map { group.getTagAt(it) },
        )
    }.orEmpty(),
    comments = comments?.comments?.map { it.toUi(uploader) }.orEmpty(),
    commentsHasMore = comments?.hasMore ?: false,
    previews = previewSet?.let { set ->
        (0 until set.size()).mapNotNull { i ->
            runCatching {
                val preview = set.getGalleryPreview(gid, i)
                PreviewThumbUi(
                    index = preview.position,
                    thumbUrl = preview.imageUrl.orEmpty(),
                    pageUrl = set.getPageUrlAt(i).orEmpty(),
                    offsetX = preview.offsetX.coerceAtLeast(0),
                    offsetY = preview.offsetY.coerceAtLeast(0),
                    clipWidth = preview.clipWidth.coerceAtLeast(0),
                    clipHeight = preview.clipHeight.coerceAtLeast(0),
                )
            }.getOrNull()
        }
    }.orEmpty(),
    previewPages = previewPages,
    apiUid = apiUid,
    apiKey = apiKey,
    ratingCount = ratingCount,
)

/** GalleryComment → UI 模型。 */
private fun com.hippo.ehviewer.client.data.GalleryComment.toUi(uploader: String? = null): CommentUi =
    CommentUi(
        id = id,
        user = user.orEmpty(),
        score = score,
        time = time,
        text = runCatching { org.jsoup.Jsoup.parse(comment.orEmpty()).text() }
            .getOrDefault(comment.orEmpty()),
        isUploader = !uploader.isNullOrBlank() && user == uploader,
        isEdited = lastEdited > 0,
        canVoteUp = voteUpAble,
        canVoteDown = voteDownAble,
        votedUp = voteUpEd,
        votedDown = voteDownEd,
        editable = editable,
    )