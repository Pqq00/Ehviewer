// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import app.eros.shared.data.CommentUi
import app.eros.shared.data.EhApi
import app.eros.shared.data.PreviewPageUi
import app.eros.shared.data.FavouritesUi
import app.eros.shared.data.GalleryDetailUi
import app.eros.shared.data.GalleryPage
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.RankPageUi
import app.eros.shared.data.ReaderPageUi
import app.eros.shared.data.SiteProfileUi
import app.eros.shared.data.TopPeriodUi
import app.eros.shared.data.WatchedTagUi
import kotlinx.coroutines.delay

/**
 * 测试用假实现：只实现 [HomeState] 用到的 [frontPage]，
 * 其余方法抛 [NotImplementedError]，避免"假实现悄悄返回空数据"。
 */
class FakeEhApi(
    private val pageSize: Int = 2,
    private val delayMs: Long = 0,
    var failure: Throwable? = null,
) : EhApi {

    var frontPageCalls = 0
        private set
    var lastCats: Int? = null
        private set

    override suspend fun frontPage(nextHref: String?, cats: Int?, forceRefresh: Boolean): GalleryPage {
        frontPageCalls++
        lastCats = cats
        if (delayMs > 0) delay(delayMs)
        failure?.let { throw it }
        val base = if (nextHref == null) 0L else 1000L
        return GalleryPage(
            items = (0 until pageSize).map { i ->
                GalleryUi(
                    gid = base + i,
                    token = "token$i",
                    title = "gallery $i",
                    thumb = null,
                    category = 0,
                )
            },
            nextHref = if (nextHref == null) "cursor-1" else null,
        )
    }

    override suspend fun popular(nextHref: String?, forceRefresh: Boolean): GalleryPage =
        GalleryPage(items = emptyList(), nextHref = null)

    override suspend fun detail(gid: Long, token: String, allComments: Boolean, forceRefresh: Boolean): GalleryDetailUi = notImplemented()
    override suspend fun postComment(gid: Long, token: String, text: String, editId: Long?): List<CommentUi> = notImplemented()
    override suspend fun voteComment(gid: Long, token: String, commentId: Long, vote: Int): Int = notImplemented()
    override suspend fun previewPage(gid: Long, token: String, page: Int): PreviewPageUi = notImplemented()
    override suspend fun readerPageUrls(gid: Long, token: String): List<String> = notImplemented()
    override suspend fun readerPage(pageUrl: String): ReaderPageUi = notImplemented()
    override suspend fun readerPageUrl(pageUrl: String): String = notImplemented()
    override suspend fun fetchTagDatabase(onProgress: (Float) -> Unit): String = notImplemented()
    override suspend fun fetchTagDatabaseIfChanged(
        knownStamp: String?,
        onProgress: (Float) -> Unit,
    ): app.eros.shared.data.TagDatabaseFetch = notImplemented()
    override suspend fun imageBytes(imageUrl: String, onProgress: (Float) -> Unit): ByteArray = notImplemented()
    override suspend fun readerImage(pageUrl: String, onProgress: (Float) -> Unit): app.eros.shared.data.ReaderImageUi = notImplemented()
    override suspend fun search(
        query: String,
        nextHref: String?,
        catsExclude: Int?,
        forceRefresh: Boolean,
    ): GalleryPage = notImplemented()
    override suspend fun searchByUploader(uploader: String, nextHref: String?): GalleryPage = notImplemented()
    override suspend fun favourites(favCat: Int, nextHref: String?): FavouritesUi = notImplemented()
    override suspend fun setFavourite(gid: Long, token: String, dstCat: Int): Boolean = notImplemented()
    override suspend fun watchedTags(): List<WatchedTagUi> = notImplemented()
    override suspend fun addWatchedTag(tag: String, watched: Boolean, hidden: Boolean): List<WatchedTagUi> = notImplemented()
    override suspend fun deleteWatchedTag(userTagId: String, weight: Int): List<WatchedTagUi> = notImplemented()
    override suspend fun topList(): List<TopPeriodUi> = notImplemented()
    override suspend fun topRated(nextHref: String?, minRating: Int): GalleryPage = notImplemented()
    override suspend fun rankList(
        tl: Int,
        page: Int,
        forceRefresh: Boolean,
        enrichTags: Boolean,
    ): RankPageUi = notImplemented()
    override suspend fun enrichGalleries(items: List<GalleryUi>): List<GalleryUi> = emptyList()
    override fun isSignedIn(): Boolean = true

    override suspend fun siteProfiles(): List<SiteProfileUi> = emptyList()

    override fun selectSiteProfile(number: Int) = Unit

    private fun <T> notImplemented(): T = throw NotImplementedError("FakeEhApi 未实现该方法")
}
