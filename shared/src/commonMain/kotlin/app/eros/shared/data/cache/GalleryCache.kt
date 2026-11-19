// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data.cache

import app.eros.shared.data.CommentUi
import app.eros.shared.data.GalleryDetailUi
import app.eros.shared.data.PreviewThumbUi
import app.eros.shared.data.GalleryPage
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.TagGroupUi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.eros.shared.currentTimeMillis
import app.eros.shared.settings.AppSettings

/**
 * P0 本地缓存：把列表页与详情页落盘（Android 用 SQLite，桌面用内存），
 * 页面先读缓存立即出内容，再后台走网络刷新。
 */
@Serializable
data class GalleryRecord(
    val gid: Long,
    val token: String,
    val title: String,
    val titleJpn: String? = null,
    val thumb: String? = null,
    val category: Int = 0,
    val uploader: String? = null,
    val pages: Int = 0,
    val rating: Float = -1f,
    val posted: String? = null,
    val tags: List<String> = emptyList(),
    val updatedAt: Long = 0L,
)

@Serializable
data class CachedPage(
    val items: List<GalleryRecord>,
    val nextHref: String?,
    val fetchedAt: Long,
)

@Serializable
data class TagGroupRecord(
    val namespace: String,
    val tags: List<String>,
)

@Serializable
data class DetailRecord(
    val gid: Long,
    val token: String,
    val title: String,
    val titleJpn: String? = null,
    val thumb: String? = null,
    val category: Int = 0,
    val uploader: String? = null,
    val posted: String? = null,
    val pages: Int = 0,
    val rating: Float = -1f,
    val favoriteCount: Int = 0,
    val isFavorited: Boolean = false,
    val language: String? = null,
    val size: String? = null,
    val tags: List<TagGroupRecord> = emptyList(),
    val ratingCount: Int = 0,
    val comments: List<CommentRecord> = emptyList(),
    val commentsHasMore: Boolean = false,
    val previews: List<PreviewThumbRecord> = emptyList(),
    val previewPages: Int = 0,
    val fetchedAt: Long,
)

@Serializable
data class CommentRecord(
    val id: Long,
    val user: String,
    val score: Int,
    val time: Long,
    val text: String,
    val isUploader: Boolean = false,
    val isEdited: Boolean = false,
)

@Serializable
data class PreviewThumbRecord(
    val index: Int,
    val thumbUrl: String,
    val pageUrl: String,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val clipWidth: Int = 0,
    val clipHeight: Int = 0,
)

@Serializable
data class HistoryEntry(
    val gid: Long,
    val token: String,
    val title: String? = null,
    val titleJpn: String? = null,
    val thumb: String? = null,
    val page: Int = 0,
    val pages: Int = 0,
    val updatedAt: Long = 0L,
)

@Serializable
data class SearchHistoryEntry(
    val query: String,
    val lastUsedAt: Long = 0L,
)

@Serializable
data class TagCountRow(
    val full: String,
    val namespace: String,
    val name: String,
    val count: Int,
)

data class TagCountMeta(
    val version: String,
    val updatedAt: Long,
)

data class TagRecord(
    val namespace: String,
    val name: String,
    val full: String,
    val translated: String? = null,
    val count: Int = 0,
    val updatedAt: Long = 0L,
)

/** /s/ 页面解析出的真实图片地址缓存，避免每次翻页都重新解析。 */
data class ImageUrlRecord(
    val gid: Long,
    val index: Int,
    val pageUrl: String,
    val imageUrl: String,
    val fetchedAt: Long = 0L,
)

data class GalleryMetaRecord(
    val gid: Long,
    val tags: List<String>,
    val rating: Float,
    val pages: Int,
    val posted: String?,
    val fetchedAt: Long,
)

interface GalleryCacheStore {
    suspend fun loadPage(key: String, index: Int, maxAgeMs: Long): CachedPage?
    suspend fun savePage(key: String, index: Int, page: CachedPage)

    suspend fun loadDetail(gid: Long, token: String, maxAgeMs: Long): DetailRecord?
    suspend fun saveDetail(record: DetailRecord)

    /** 写入/更新画廊元数据（详情加载后回填用）。 */
    suspend fun upsertGalleries(records: List<GalleryRecord>) {}

    suspend fun prune(now: Long, pageMaxAgeMs: Long, detailMaxAgeMs: Long)

    /** 浏览记录：按 gid 覆盖写入，读取时按 updated_at 倒序。 */
    suspend fun recordHistory(gid: Long, token: String, page: Int, pages: Int) {}

    suspend fun loadHistory(limit: Int): List<HistoryEntry> = emptyList()

    suspend fun deleteHistory(gid: Long) {}

    /** 打开详情时更新最近浏览（保留已有阅读页码）。 */
    suspend fun recordHistoryVisit(gid: Long, token: String, pages: Int) {}

    /** 搜索历史：去重后按最近使用时间排序，最多保留 50 条。 */
    suspend fun recordSearch(query: String) {}

    suspend fun loadSearchHistory(limit: Int): List<SearchHistoryEntry> = emptyList()

    suspend fun clearSearchHistory() {}

    /** 标签表（词频/翻译/详情预留）。 */
    suspend fun saveTagCounts(rows: List<TagCountRow>) {}
    suspend fun searchTags(prefix: String, limit: Int = 20): List<TagRecord> = emptyList()
    suspend fun tagCountMeta(): TagCountMeta? = null
    suspend fun saveTagCountMeta(meta: TagCountMeta) {}
    suspend fun upsertTags(tags: List<TagRecord>) {}

    suspend fun loadTags(fulls: List<String>): Map<String, TagRecord> = emptyMap()

    suspend fun loadImageUrl(gid: Long, index: Int): ImageUrlRecord? = null
    suspend fun saveImageUrl(record: ImageUrlRecord) {}

    suspend fun deleteImageUrl(gid: Long, index: Int) {}

    suspend fun loadMeta(gids: List<Long>): Map<Long, GalleryMetaRecord> = emptyMap()

    suspend fun saveMeta(records: List<GalleryMetaRecord>) {}

    /**
     * 只清「列表页」缓存，详情/历史/收藏/标签都保留。
     *
     * 站点 Profile 切换后页面布局、缩略图尺寸、标题语言都可能变，
     * 列表页缓存因此失效，需要重新拉一次。
     */
    suspend fun clearListPages() {}

    /** 测试/登出用：清空全部缓存。 */
    suspend fun clear()

    /** 解析模型缓存当前占用字节数（设置页展示）；不支持的平台返回 0。 */
    suspend fun usageBytes(): Long = 0L
}

expect fun createGalleryCacheStore(): GalleryCacheStore

object GalleryCache {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * 列表/详情模型的缓存有效期，与 core 的 `HttpCachePolicy` 口径对齐：
     * 列表页 1 小时、详情 6 小时。命中后都是「先出内容、后台再刷新」，
     * 所以短 TTL 只影响「冷启动首屏是否还显示旧内容」，不影响随时手动刷新。
     */
    const val PAGE_MAX_AGE_MS: Long = 60L * 60 * 1000
    const val DETAIL_MAX_AGE_MS: Long = 6L * 60 * 60 * 1000

    /**
     * 解析模型缓存的结构版本：字段含义变化（例如标题语言、站点作用域）时 +1，
     * 旧 key 自然不再命中，随后由 [pruneIfNeeded] 清掉。
     */
    private const val CACHE_SCHEMA = "v2"

    private val store: GalleryCacheStore by lazy { createGalleryCacheStore() }

    private var pruned = false

    /**
     * 列表页 key 前缀：站点 + 登录态 + 结构版本。
     *
     * 未登录/已登录看到的首页、搜索结果不同（收藏数、标签可见性、部分分区），
     * 只用站点前缀会让登出后继续命中登录态的列表。
     */
    private fun sitePrefix(): String {
        val site = if (AppSettings.exHentai) "ex" else "e"
        val signed = if (com.hippo.ehviewer.net.EhAuth.hasSignedIn()) "in" else "out"
        return "$CACHE_SCHEMA|$site|$signed"
    }

    fun homeKey(cats: Int): String = "${sitePrefix()}:home:$cats"
    fun rankKey(tl: Int): String = "${sitePrefix()}:rank:$tl"

    /** 首页自定义分组的列表缓存键（按分组 id，改名/换顺序不影响缓存）。 */
    fun groupKey(id: String): String = "${sitePrefix()}:home:g:$id"

    /** 全站热门列表的缓存键。 */
    fun popularKey(): String = "${sitePrefix()}:popular"
    fun searchKey(uploader: Boolean, query: String): String =
        "${sitePrefix()}:" + (if (uploader) "uploader:" else "search:") + query.trim().lowercase()

    fun favKey(cat: Int): String = "${sitePrefix()}:fav:$cat"

    suspend fun recordHistory(gid: Long, token: String, page: Int, pages: Int) =
        store.recordHistory(gid, token, page, pages)

    suspend fun history(limit: Int = 100): List<HistoryEntry> = store.loadHistory(limit)

    suspend fun deleteHistory(gid: Long) = store.deleteHistory(gid)

    suspend fun recordHistoryVisit(gid: Long, token: String, pages: Int) =
        store.recordHistoryVisit(gid, token, pages)

    suspend fun recordSearch(query: String) {
        val q = query.trim()
        if (q.isNotEmpty()) store.recordSearch(q)
    }

    suspend fun searchHistory(limit: Int = 30): List<SearchHistoryEntry> = store.loadSearchHistory(limit)

    suspend fun clearSearchHistory() = store.clearSearchHistory()

    suspend fun saveTags(tags: List<TagRecord>) = store.upsertTags(tags)

    suspend fun imageUrl(gid: Long, index: Int): ImageUrlRecord? = store.loadImageUrl(gid, index)

    suspend fun saveImageUrl(record: ImageUrlRecord) = store.saveImageUrl(record)

    suspend fun deleteImageUrl(gid: Long, index: Int) = store.deleteImageUrl(gid, index)

    suspend fun loadMeta(gids: List<Long>): Map<Long, GalleryMetaRecord> = store.loadMeta(gids)

    suspend fun saveMeta(records: List<GalleryMetaRecord>) = store.saveMeta(records)

    suspend fun saveTagCounts(rows: List<TagCountRow>) = store.saveTagCounts(rows)

    suspend fun searchTags(prefix: String, limit: Int = 20): List<TagRecord> =
        store.searchTags(prefix, limit)

    suspend fun tagCountMeta(): TagCountMeta? = store.tagCountMeta()

    suspend fun saveTagCountMeta(meta: TagCountMeta) = store.saveTagCountMeta(meta)

    suspend fun tags(fulls: List<String>): Map<String, TagRecord> = store.loadTags(fulls)

    fun now(): Long = currentTimeMillis()

    /** 冷启动清一次过期数据；只执行一次。 */
    suspend fun pruneIfNeeded() {
        if (pruned) return
        pruned = true
        runCatching {
            val n = now()
            store.prune(
                now = n,
                pageMaxAgeMs = PAGE_MAX_AGE_MS,
                detailMaxAgeMs = DETAIL_MAX_AGE_MS,
            )
        }
    }

    suspend fun page(key: String, index: Int = 0): GalleryPage? = runCatching {
        val cached = store.loadPage(key, index, PAGE_MAX_AGE_MS) ?: return@runCatching null
        val items = cached.items.map { it.toUi() }
        if (items.isEmpty()) null else GalleryPage(items = items, nextHref = cached.nextHref)
    }.getOrNull()

    /** 列表当前已加载的完整内容写回第 0 页，冷启动可整体恢复。 */
    suspend fun saveMerged(key: String, items: List<GalleryUi>, nextHref: String?) {
        if (items.isEmpty()) return
        runCatching {
            val now = now()
            store.savePage(
                key,
                0,
                CachedPage(
                    items = items.map { it.toRecord(now) },
                    nextHref = nextHref,
                    fetchedAt = now,
                ),
            )
        }
    }

    suspend fun detail(gid: Long, token: String): GalleryDetailUi? = runCatching {
        store.loadDetail(gid, token, DETAIL_MAX_AGE_MS)?.toUi()
    }.getOrNull()

    suspend fun clearAll() {
        runCatching { store.clear() }
    }

    /** 解析模型缓存占用字节数（设置页展示用）。 */
    suspend fun usageBytes(): Long = runCatching { store.usageBytes() }.getOrDefault(0L)

    /** 站点 Profile 切换后清列表页缓存，让列表下次重新拉取。 */
    suspend fun clearListPages() {
        runCatching { store.clearListPages() }
    }

    suspend fun saveDetail(detail: GalleryDetailUi, token: String) {
        runCatching {
            val stamp = now()
            store.saveDetail(detail.toRecord(token, stamp))
            // 详情里带日文标题，顺手回填画廊行，让历史/下载也能显示日文名
            store.upsertGalleries(
                listOf(
                    GalleryRecord(
                        gid = detail.gid,
                        token = token,
                        title = detail.title,
                        titleJpn = detail.titleJpn,
                        thumb = detail.thumb,
                        category = detail.category,
                        uploader = detail.uploader,
                        pages = detail.pages,
                        rating = detail.rating,
                        posted = detail.posted,
                        tags = detail.tags.flatMap { group -> group.tags.map { "${group.namespace}:$it" } },
                        updatedAt = stamp,
                    ),
                ),
            )
        }
    }

    fun GalleryUi.toRecord(now: Long): GalleryRecord = GalleryRecord(
        gid = gid,
        token = token,
        title = title,
        titleJpn = titleJpn,
        thumb = thumb,
        category = category,
        uploader = uploader,
        pages = pages,
        rating = rating,
        posted = posted,
        tags = tags,
        updatedAt = now,
    )

    fun GalleryRecord.toUi(): GalleryUi = GalleryUi(
        gid = gid,
        token = token,
        title = title,
        titleJpn = titleJpn,
        thumb = thumb,
        category = category,
        tags = tags,
        uploader = uploader,
        pages = pages,
        rating = rating,
        posted = posted,
    )

    fun GalleryDetailUi.toRecord(token: String, now: Long): DetailRecord = DetailRecord(
        gid = gid,
        token = token,
        title = title,
        titleJpn = titleJpn,
        thumb = thumb,
        category = category,
        uploader = uploader,
        posted = posted,
        pages = pages,
        rating = rating,
        favoriteCount = favoriteCount,
        isFavorited = isFavorited,
        language = language,
        size = size,
        tags = tags.map { TagGroupRecord(it.namespace, it.tags) },
        ratingCount = ratingCount,
        comments = comments.map { CommentRecord(it.id, it.user, it.score, it.time, it.text, it.isUploader, it.isEdited) },
        commentsHasMore = commentsHasMore,
        previews = previews.map { PreviewThumbRecord(it.index, it.thumbUrl, it.pageUrl, it.offsetX, it.offsetY, it.clipWidth, it.clipHeight) },
        previewPages = previewPages,
        fetchedAt = now,
    )

    fun DetailRecord.toUi(): GalleryDetailUi = GalleryDetailUi(
        gid = gid,
        title = title,
        titleJpn = titleJpn,
        thumb = thumb,
        category = category,
        uploader = uploader,
        posted = posted,
        pages = pages,
        rating = rating,
        favoriteCount = favoriteCount,
        isFavorited = isFavorited,
        favoriteName = null,
        language = language,
        size = size,
        tags = tags.map { TagGroupUi(it.namespace, it.tags) },
        comments = comments.map { CommentUi(it.id, it.user, it.score, it.time, it.text, it.isUploader, it.isEdited) },
        commentsHasMore = commentsHasMore,
        previews = previews.map {
            PreviewThumbUi(it.index, it.thumbUrl, it.pageUrl, it.offsetX, it.offsetY, it.clipWidth, it.clipHeight)
        },
        previewPages = previewPages,
        ratingCount = ratingCount,
    )

    fun encodeDetail(record: DetailRecord): String = json.encodeToString(DetailRecord.serializer(), record)
    fun decodeDetail(text: String): DetailRecord? = runCatching {
        json.decodeFromString(DetailRecord.serializer(), text)
    }.getOrNull()
}
