// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

/** UI 层使用的精简模型，避免把 EhViewer 的 Java 模型泄进 commonMain。 */
data class GalleryUi(
    val gid: Long,
    val token: String,
    val title: String,
    /** 日文原名（详情/API 才有；列表页为 null）。 */
    val titleJpn: String? = null,
    val thumb: String?,
    val category: Int,
    /** 列表页解析出的标签，格式 `namespace:name`。 */
    val tags: List<String> = emptyList(),
    val uploader: String? = null,
    /** 页数，0 表示列表页没给出。 */
    val pages: Int = 0,
    /** 列表页给出的评分；-1 表示没有。 */
    val rating: Float = -1f,
    /** 列表页给出的发布时间（完整字符串，如 2024-05-01 12:34）；null 表示没有。 */
    val posted: String? = null,
)

/** 一页画廊 + 下一页的游标地址（null = 没有下一页）。 */
data class GalleryPage(
    val items: List<GalleryUi>,
    val nextHref: String?,
)

/** 关注标签（来自 `/watched`）。 */
data class WatchedTagUi(
    val tag: String,
    val watched: Boolean,
    val hidden: Boolean,
    val weight: Int,
    /** 形如 `usertag_123`，删除时要用。 */
    val id: String = "",
)

/** 榜单条目（画廊榜）。 */
data class TopGalleryUi(
    val rank: Int,
    val title: String,
    val gid: Long,
    val token: String,
)

/** 榜单的一个时段（今日/本月/今年/全部）。 */
data class TopPeriodUi(
    val label: String,
    val items: List<TopGalleryUi>,
)

/** 排行榜一页。 */
data class RankPageUi(
    val items: List<GalleryUi>,
    val nextPage: Int?,
    val totalPages: Int,
)

/** 榜单时段 → 站点 tl 参数。 */
object RankPeriod {
    const val DAY = 15
    const val MONTH = 13
    const val YEAR = 12
    const val ALL = 11

    val entries = listOf(
        "今日" to DAY,
        "本月" to MONTH,
        "今年" to YEAR,
        "全部" to ALL,
    )
}

data class TagGroupUi(
    val namespace: String,
    val tags: List<String>,
)

/** 一条评论。 */
data class CommentUi(
    val id: Long,
    val user: String,
    val score: Int,
    val time: Long,
    val text: String,
    val isUploader: Boolean = false,
    val isEdited: Boolean = false,
    val canVoteUp: Boolean = false,
    val canVoteDown: Boolean = false,
    val votedUp: Boolean = false,
    val votedDown: Boolean = false,
    val editable: Boolean = false,
)

/** 一张预览缩略图。 */
data class PreviewThumbUi(
    val index: Int,
    val thumbUrl: String,
    val pageUrl: String,
    /** 预览图是"雪碧图"：同一张图用 offset/clip 切出不同页，0 或负数表示无需裁剪。 */
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val clipWidth: Int = 0,
    val clipHeight: Int = 0,
)

/** 一页预览缩略图（20 张）+ 总页数。 */
data class PreviewPageUi(
    val thumbs: List<PreviewThumbUi>,
    val totalPreviewPages: Int,
)

data class GalleryDetailUi(
    val gid: Long,
    val title: String,
    /** 日文原名，可能为 null。 */
    val titleJpn: String? = null,
    val thumb: String?,
    val category: Int,
    val uploader: String?,
    val posted: String?,
    val pages: Int,
    val rating: Float,
    val favoriteCount: Int,
    val isFavorited: Boolean,
    /** 收藏夹名（如 "Favorites 0" / 自定义名），未收藏为 null。 */
    val favoriteName: String? = null,
    val language: String?,
    val size: String?,
    val tags: List<TagGroupUi>,
    val comments: List<CommentUi> = emptyList(),
    val commentsHasMore: Boolean = false,
    val previews: List<PreviewThumbUi> = emptyList(),
    val previewPages: Int = 0,
    val apiUid: Long = -1,
    val apiKey: String? = null,
    val ratingCount: Int = 0,
)

data class ReaderPageUi(
    val imageUrl: String,
)

/** [EhApi.readerImage] 的返回值：图片字节 + 实际使用的原图地址（供上层缓存地址）。 */
data class ReaderImageUi(
    val bytes: ByteArray,
    val imageUrl: String,
)

/**
 * [EhApi.fetchTagDatabaseIfChanged] 的返回值。
 *
 * [json] 为 null 表示服务端版本没变、这次没有下载；[stamp] 是服务端版本标记，
 * 上层存下来下次用作「有没有变」的比对依据。
 */
data class TagDatabaseFetch(
    val json: String?,
    val stamp: String?,
)

interface EhApi {
    /**
     * 首页列表：第一页传 null，之后传上一页的 [GalleryPage.nextHref]。
     * [cats] 是「保留的分类位掩码」，null 表示不筛选。
     * [forceRefresh] = true 时绕过 10 分钟页面缓存（下拉刷新用）。
     */
    suspend fun frontPage(
        nextHref: String? = null,
        cats: Int? = null,
        forceRefresh: Boolean = false,
    ): GalleryPage

    /** 全站热门（站点 `/popular`），分页用 [nextHref]。 */
    suspend fun popular(
        nextHref: String? = null,
        forceRefresh: Boolean = false,
    ): GalleryPage

    /** 画廊详情；[allComments] = true 时带 `hc=1` 展开全部评论。 */
    suspend fun detail(
        gid: Long,
        token: String,
        allComments: Boolean = false,
        forceRefresh: Boolean = false,
    ): GalleryDetailUi

    /** 发表评论（[editId] 非空为编辑），返回最新评论列表。 */
    suspend fun postComment(gid: Long, token: String, text: String, editId: Long? = null): List<CommentUi>

    /** 给评论投票：+1 赞 / -1 踩；返回新的分数。 */
    suspend fun voteComment(gid: Long, token: String, commentId: Long, vote: Int): Int

    /** 某一页预览缩略图。 */
    suspend fun previewPage(gid: Long, token: String, page: Int): PreviewPageUi

    /** 阅读器：详情页预览集里的前若干页 /s/ 地址。 */
    suspend fun readerPageUrls(gid: Long, token: String): List<String>

    /** 解析单页：图片地址 + 下一页 /s/ 地址。 */
    suspend fun readerPage(pageUrl: String): ReaderPageUi

    /**
     * 只解析 `/s/` 页面拿原图地址（约 5KB，弱网 0.4s）。
     *
     * 单独留一个入口，是为了让地址解析能和图片下载分开调度：
     * 地址是小请求，不该占着下载槽位，也不该被别的大图堵住。
     */
    suspend fun readerPageUrl(pageUrl: String): String

    /** 拉取标签翻译数据库 JSON。 */
    suspend fun fetchTagDatabase(onProgress: (Float) -> Unit): String

    /**
     * 标签库「有变化才下载」：先 HEAD 探一次版本标记（几百字节），
     * 与 [knownStamp] 相同就直接返回 `json = null`，不再拉那 4 MB。
     *
     * 设置页把「关→开」当作刷新手势，常态下应该只有这一次 HEAD 的流量。
     */
    suspend fun fetchTagDatabaseIfChanged(
        knownStamp: String?,
        onProgress: (Float) -> Unit,
    ): TagDatabaseFetch

    /** 下载一页图片字节，回调 0..1 进度。 */
    suspend fun imageBytes(imageUrl: String, onProgress: (Float) -> Unit): ByteArray

    /**
     * 阅读器一次拿齐一页：解析 `/s/` 页面 → 下载原图（进度回调 0..1）。
     *
     * 之所以合成一个调用：解析页面和下载图片要顺序执行，合成后可以在同一处用上
     * 「被 H@H 限流时带 `nl=` 重取」的补救逻辑，而不必每页先发一个 HEAD 探测。
     */
    suspend fun readerImage(pageUrl: String, onProgress: (Float) -> Unit): ReaderImageUi

    /**
     * 搜索/筛选。nextHref 为游标，第一页传 null。
     *
     * [catsExclude] 是站点 `f_cats`（要排除的分类位），0/null 表示不排除；
     * 首页的自定义分组把它当作「限定分类」用，见 [app.eros.shared.settings.HomeEntry.excludeCats]。
     */
    suspend fun search(
        query: String,
        nextHref: String? = null,
        catsExclude: Int? = null,
        forceRefresh: Boolean = false,
    ): GalleryPage

    /** 按上传者搜索。 */
    suspend fun searchByUploader(uploader: String, nextHref: String? = null): GalleryPage

    /** 收藏夹：favCat 0..9；nextHref 为上一页返回的游标（第一页传 null）。 */
    suspend fun favourites(favCat: Int, nextHref: String? = null): FavouritesUi

    /** 加入/移出收藏：dstCat 0..9，或 -1 表示移出。 */
    suspend fun setFavourite(gid: Long, token: String, dstCat: Int): Boolean

    /** 关注标签列表（需要登录）。 */
    suspend fun watchedTags(): List<WatchedTagUi>

    /** 添加关注标签，返回更新后的列表。 */
    suspend fun addWatchedTag(tag: String, watched: Boolean, hidden: Boolean): List<WatchedTagUi>

    /** 删除关注标签（[userTagId] 形如 `usertag_123` 或纯数字），返回更新后的列表。 */
    suspend fun deleteWatchedTag(userTagId: String, weight: Int): List<WatchedTagUi>

    /** 画廊排行榜：4 个时段。 */
    suspend fun topList(): List<TopPeriodUi>

    /** 高分作品分页流（保留，搜索用）。 */
    suspend fun topRated(nextHref: String? = null, minRating: Int = 4): GalleryPage

    /**
     * 排行榜分页：`tl` 见 [RankPeriod]，`page` 从 0 开始。
     * 返回该页条目 + 下一页页码（null = 没有下一页）。
     */
    suspend fun rankList(
        tl: Int,
        page: Int,
        forceRefresh: Boolean = false,
        enrichTags: Boolean = false,
    ): RankPageUi

    /**
     * 用 `api.php` 的 gdata 补全列表项（标签 / 评分 / 页数 / 发布时间 / 封面）。
     *
     * 只返回确实补全成功的项；失败项保持原样，由调用方决定是否重试。
     */
    suspend fun enrichGalleries(items: List<GalleryUi>): List<GalleryUi>

    /** 已登录？ */
    fun isSignedIn(): Boolean

    /**
     * 站点侧 Profile（uconfig.php 里的配置档案）列表。
     *
     * 站点把显示/过滤偏好存成多套档案，靠 cookie `sp` 决定用哪套；未登录会拿不到列表（抛异常）。
     */
    suspend fun siteProfiles(): List<SiteProfileUi>

    /**
     * 切换站点 Profile，只写 cookie `sp`。
     *
     * 已经取到的列表不会变，新页面才按新档案渲染，所以调用方宜提示用户刷新。
     */
    fun selectSiteProfile(number: Int)
}

/** 站点 Profile 的一个档案。 */
data class SiteProfileUi(
    val number: Int,
    val name: String,
    val selected: Boolean,
)

data class FavouritesUi(
    val items: List<GalleryUi>,
    val catNames: List<String>,
    val catCounts: List<Int>,
    val nextHref: String?,
)

/** 平台实现：Android / 桌面各自桥接到 :core 的 EhApi。 */
expect fun createEhApi(): EhApi
