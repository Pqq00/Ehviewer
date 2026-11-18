// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client

import com.alibaba.fastjson.JSONObject
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.data.EhTopListDetail
import com.hippo.ehviewer.client.data.GalleryCommentList
import com.hippo.ehviewer.client.data.GalleryDetail
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.client.data.HomeDetail
import com.hippo.ehviewer.client.data.PreviewPage
import com.hippo.ehviewer.client.data.SiteProfile
import com.hippo.ehviewer.client.data.UserTag
import com.hippo.ehviewer.client.exception.EhException
import com.hippo.ehviewer.client.exception.HttpStatusException
import com.hippo.ehviewer.client.parser.ArchiveParser
import com.hippo.ehviewer.client.parser.EhEventParse
import com.hippo.ehviewer.client.parser.EhHomeParser
import com.hippo.ehviewer.client.parser.FavoritesParser
import com.hippo.ehviewer.client.parser.GalleryApiParser
import com.hippo.ehviewer.client.parser.GalleryDetailParser
import com.hippo.ehviewer.client.parser.GalleryListParser
import com.hippo.ehviewer.client.parser.GalleryPageParser
import com.hippo.ehviewer.client.parser.ListParseRetry
import com.hippo.ehviewer.client.parser.MyTagLitParser
import com.hippo.ehviewer.client.parser.RankListParser
import com.hippo.ehviewer.client.parser.TopListParser
import com.hippo.ehviewer.client.parser.TorrentParser
import com.hippo.ehviewer.client.parser.VoteCommentParser
import com.hippo.ehviewer.net.EhAuth
import com.hippo.ehviewer.net.EhCalls
import com.hippo.ehviewer.net.EhCookieJar
import com.hippo.ehviewer.net.EhHttp
import com.hippo.ehviewer.net.EhSiteErrorKind
import com.hippo.ehviewer.net.EhSiteException
import com.hippo.ehviewer.net.HttpCacheBridgeHolder
import com.hippo.ehviewer.net.HttpCacheKeys
import com.hippo.ehviewer.net.NetLog
import com.hippo.ehviewer.net.ResponseClassifier
import com.hippo.ehviewer.net.RetryAfter
import com.hippo.ehviewer.net.cachePolicyFor
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream

/** 列表的一页。`nextHref` 为 null 表示已到末页。 */
data class GalleryListPage(val items: List<GalleryInfo>, val nextHref: String?)

/** 阅读器的一页：原图地址。 */
data class ReaderPageData(val imageUrl: String)

/**
 * 站点各接口的门面：URL 拼装、请求收发、响应分类、交给 parser 解析。
 *
 * 这一层只负责「发什么请求、拿到什么响应、异常怎么归类」，不持有线程池、数据库、
 * 回调或界面状态；调用方在自己的协程里阻塞等待即可。请求去重、HTTP 缓存、取消
 * 与网络日志都在 [request] 这一条路径上统一处理。
 */
object EhEngine {

    private const val IMAGE_UA_HEADER = "User-Agent"

    /** api.php 单次 `gidlist` 的硬上限，超出站点会直接报 too many gidlist requests。 */
    private const val API_BATCH_LIMIT = 25

    // ============================================================ 列表

    /**
     * 首页/列表页。首屏传 null，翻页传上一页返回的 [GalleryListPage.nextHref]。
     *
     * `f_cats=0` 会被站点拖到 40s 以上才回（实测），所以没有排除项时干脆不传这个参数。
     */
    @JvmStatic
    fun getGalleryList(
        nextHref: String? = null,
        fCats: Int? = null,
        forceRefresh: Boolean = false,
        cancelKey: EhCalls.Handle? = null,
    ): GalleryListPage {
        val url = when {
            !nextHref.isNullOrBlank() && nextHref.startsWith("http") -> nextHref
            !nextHref.isNullOrBlank() -> siteHost() + nextHref.trimStart('/')
            fCats != null && fCats != 0 -> homeUrl() + "?f_cats=" + fCats
            else -> homeUrl()
        }
        return listWithFallback(url, forceRefresh, cancelKey)
    }

    /**
     * 搜索页。关键词走 `f_search`，支持 `artist:name$` 这类标签语法；
     * 分页用游标 `?next=<gid>`，和普通列表同一套解析。
     */
    @JvmStatic
    fun searchGalleryList(
        query: String,
        catsExclude: Int? = null,
        nextHref: String? = null,
        minRating: Int? = null,
        forceRefresh: Boolean = false,
        cancelKey: EhCalls.Handle? = null,
    ): GalleryListPage {
        if (!nextHref.isNullOrBlank()) {
            val url = if (nextHref.startsWith("http")) nextHref else siteHost() + nextHref.trimStart('/')
            return listWithFallback(url, forceRefresh, cancelKey)
        }
        val query0 = StringBuilder(siteHost())
        query0.append("?f_search=").append(URLEncoder.encode(query, "UTF-8"))
        if (catsExclude != null && catsExclude != 0) {
            query0.append("&f_cats=").append(catsExclude)
        }
        if (minRating != null) {
            query0.append("&f_sr=on&f_srdd=").append(minRating)
        }
        return listWithFallback(query0.toString(), forceRefresh, cancelKey)
    }

    /** 按上传者查：站点走路径 `/uploader/<name>`，不是 f_search。 */
    @JvmStatic
    fun searchUploaderList(
        uploader: String,
        nextHref: String? = null,
        cancelKey: EhCalls.Handle? = null,
    ): GalleryListPage {
        val url = when {
            !nextHref.isNullOrBlank() && nextHref.startsWith("http") -> nextHref
            !nextHref.isNullOrBlank() -> siteHost() + nextHref.trimStart('/')
            else -> siteHost() + "uploader/" + URLEncoder.encode(uploader, "UTF-8")
        }
        return toPage(GalleryListParser.parse(fetchText(url, cancelKey = cancelKey), 0))
    }

    /**
     * 热门页 `/popular`。翻页靠日期窗口往前滚，所以游标不在 `#unext` 里，
     * 而是页面脚本的 `nexturl` 变量；两处都看一眼，优先用解析器给的。
     */
    @JvmStatic
    fun getPopularList(
        nextHref: String? = null,
        forceRefresh: Boolean = false,
        cancelKey: EhCalls.Handle? = null,
    ): GalleryListPage {
        val url = when {
            !nextHref.isNullOrBlank() && nextHref.startsWith("http") -> nextHref
            !nextHref.isNullOrBlank() -> siteHost() + nextHref.trimStart('/')
            else -> siteHost() + "popular"
        }
        val body = fetchText(url, forceRefresh, cancelKey)
        val parsed = GalleryListParser.parse(body, 0)
        val fromNav = parsed.nextHref?.takeIf { it.isNotBlank() }
        val fromScript = Regex("nexturl=\"([^\"]*)\"")
            .find(body)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        return GalleryListPage(parsed.galleryInfoList, fromNav ?: fromScript)
    }

    /**
     * 取列表；解析结果为空但响应确实是列表页时，带 `inline_set=dm_l` 强制扩展视图再要一次。
     * 站点偶尔会返回「未加载完」的空壳页面，重试一次基本都能出内容。
     */
    private fun listWithFallback(
        url: String,
        forceRefresh: Boolean = false,
        cancelKey: EhCalls.Handle? = null,
    ): GalleryListPage {
        val body = fetchText(url, forceRefresh, cancelKey)
        var parsed = GalleryListParser.parse(body, 0)
        if (ListParseRetry.shouldRetry(body, parsed.galleryInfoList.size)) {
            val retryUrl = ListParseRetry.withInlineSet(url)
            NetLog.log("RETRY", retryUrl, 0, 0, 0, "列表为空，改用扩展视图重试")
            parsed = GalleryListParser.parse(fetchText(retryUrl, forceRefresh, cancelKey), 0)
        }
        return toPage(parsed)
    }

    private fun toPage(parsed: GalleryListParser.Result): GalleryListPage = GalleryListPage(
        items = parsed.galleryInfoList,
        nextHref = parsed.nextHref?.takeIf { it.isNotBlank() && it != "#" },
    )

    // ============================================================ 阅读器

    /** 详情页预览集里的前 N 页 `/s/` 地址。 */
    @JvmStatic
    fun getReaderPageUrls(gid: Long, token: String): List<String> {
        val previews = getPreviewSet(gid, token) ?: return emptyList()
        return (0 until previews.size()).mapNotNull { index ->
            previews.getPageUrlAt(index)?.takeIf { it.isNotBlank() }
        }
    }

    /** 详情页缩略图分页的元信息：最后一页序号 + 第一页的页地址。 */
    data class GalleryPaging(val pMax: Int, val firstLinks: List<String>)

    /** `?p=0`：总页数（站点上限）与第一页的 20 个页地址。 */
    @JvmStatic
    fun galleryPagingMeta(gid: Long, token: String, cancelKey: EhCalls.Handle? = null): GalleryPaging {
        val body = fetchText("${siteHost()}g/$gid/$token/?p=0", cancelKey = cancelKey)
        return GalleryPaging(maxPagingIndex(body), pageLinks(body))
    }

    /** `?p=N`：该分页里的 20 个页地址。 */
    @JvmStatic
    fun galleryPagingBatch(
        gid: Long,
        token: String,
        p: Int,
        cancelKey: EhCalls.Handle? = null,
    ): List<String> = pageLinks(fetchText("${siteHost()}g/$gid/$token/?p=$p", cancelKey = cancelKey))

    private fun pageLinks(body: String): List<String> =
        Jsoup.parse(body).select("a[href*=/s/]").map { it.attr("href") }

    private fun maxPagingIndex(body: String): Int =
        Regex("""[?&]p=(\d+)""").findAll(body)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .maxOrNull() ?: 0

    /**
     * 一次性抓全部页地址：详情页缩略图分页每页 20 张，抓 0..pMax 就齐了。
     * 请求数 = ceil(页数/20)，各分页并发（4 路），完成后按 `/s/` 里的页序号排序。
     */
    @JvmStatic
    fun getGalleryPageUrls(
        gid: Long,
        token: String,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): List<String> {
        val base = "${siteHost()}g/$gid/$token/"
        val firstBody = fetchText("$base?p=0")
        val pMax = maxPagingIndex(firstBody)
        val wanted = pMax + 1
        val urls = Collections.synchronizedList(mutableListOf<String>())
        urls.addAll(pageLinks(firstBody))
        onProgress?.invoke(1, wanted)
        if (pMax <= 0) {
            return normalizePageUrls(urls)
        }

        val workers = Executors.newFixedThreadPool(PAGING_CONCURRENCY)
        val finished = AtomicInteger(1)
        try {
            val jobs = (1..pMax).map { index ->
                workers.submit {
                    runCatching {
                        urls.addAll(pageLinks(fetchText("$base?p=$index")))
                    }
                    onProgress?.invoke(finished.incrementAndGet(), wanted)
                }
            }
            jobs.forEach { it.get() }
        } finally {
            workers.shutdown()
        }
        return normalizePageUrls(urls)
    }

    private fun normalizePageUrls(raw: List<String>): List<String> {
        val base = siteHost().trimEnd('/')
        return raw.asSequence()
            .map { if (it.startsWith("http")) it else "$base/" + it.trimStart('/') }
            .distinct()
            .sortedBy(::pageIndexIn)
            .toList()
    }

    /** `/s/<hash>/<gid>-<index>` 里的序号。 */
    private fun pageIndexIn(url: String): Int =
        Regex("""-(\d+)(?:[/?#]|$)""").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    /** 阅读器单页（只解析，不下载）。 */
    @JvmStatic
    fun getReaderPage(pageUrl: String, cancelKey: EhCalls.Handle? = null): ReaderPageData =
        ReaderPageData(parseReaderPage(absoluteUrl(pageUrl), cancelKey).imageUrl)

    /** 阅读器一页的完整结果：图片字节 + 实际取图地址（方便上层把地址写进缓存）。 */
    class ReaderImageData(
        val bytes: ByteArray,
        val imageUrl: String,
    )

    /**
     * 阅读器取一页图片：先解析 `/s/` 页面，再下载原图。
     *
     * 不做 HEAD 预探 H@H 限流（每页多一次往返，弱网下等于白烧几百毫秒）；
     * 下载真被限流（509/403）时，拿页面里已经解析出的 `skipHathKey` 拼 `nl=` 重新取一次。
     */
    @JvmStatic
    fun readerImage(pageUrl: String, onProgress: (Float) -> Unit): ReaderImageData {
        val target = absoluteUrl(pageUrl)
        val page = parseReaderPage(target)
        return try {
            ReaderImageData(downloadImageWithProgress(page.imageUrl, onProgress), page.imageUrl)
        } catch (e: HttpStatusException) {
            val retryUrl = if (e.code == 509 || e.code == 403) appendNlKeyword(target, page.skipHathKey) else null
            if (retryUrl == null) throw e
            NetLog.log("RETRY", target, e.code, 0, 0, "图片被 H@H 限流，带 nl 重新取图")
            val again = parseReaderPage(retryUrl)
            ReaderImageData(downloadImageWithProgress(again.imageUrl, onProgress), again.imageUrl)
        }
    }

    private class ReaderPage(val imageUrl: String, val skipHathKey: String?)

    private fun parseReaderPage(url: String, cancelKey: EhCalls.Handle? = null): ReaderPage {
        val parsed = GalleryPageParser.parse(fetchText(url, cancelKey = cancelKey))
        return ReaderPage(parsed.imageUrl, parsed.skipHathKey)
    }

    private fun appendNlKeyword(url: String, key: String?): String? {
        if (key.isNullOrBlank()) return null
        return url + (if (url.contains('?')) '&' else '?') + "nl=" + key
    }

    // ============================================================ 账号 / 个人中心

    /** 登录；返回登录后的页面（调用方只关心是否成功）。 */
    @JvmStatic
    fun signIn(username: String, password: String): String = EhAuth.signIn(username, password)

    /** 个人中心（配额 / GP / 管理力），需要登录态。 */
    @JvmStatic
    fun getHomeDetail(): HomeDetail = EhHomeParser.parse(fetchText(homeUrl()))

    /** 重置图片配额。 */
    @JvmStatic
    fun resetLimit(): Boolean = !fetchText(homeUrl() + "?resetimglimit=1").contains("Reset Cost")

    /** 登录态下的站点 Profile 列表。拿不到表单（未登录）时抛 [IOException]。 */
    fun fetchSiteProfiles(): List<SiteProfile> {
        val document = Jsoup.parse(fetchText("${siteHost()}uconfig.php", forceRefresh = true))
        val options = document.select("#profile_form select option")
        if (options.isEmpty()) throw IOException("站点没有返回 Profile 列表")
        return options.mapNotNull { option ->
            val number = option.attr("value").trim().toIntOrNull() ?: return@mapNotNull null
            SiteProfile(
                number = number,
                name = option.text().trim().ifBlank { "Profile $number" },
                selected = option.hasAttr("selected"),
            )
        }
    }

    /** 切换站点 Profile：站点只认 cookie `sp`，下一次请求才生效，已缓存的页面不受影响。 */
    fun selectSiteProfile(number: Int) {
        EhCookieJar.put("sp", number.toString(), if (Settings.isExHentai) "exhentai.org" else "e-hentai.org")
    }

    // ============================================================ 画廊详情

    /**
     * 画廊详情。
     *
     * 「画廊还在不在」的最终依据是 api.php 的 gdata，不是页面文案：404 的
     * `Gallery not found` 也可能是 token 过期（画廊增删页后旧链接失效）。所以凡是
     * 「疑似已删除」都先问一次 gdata——能拿到 metadata 就说明活着，顺手换新 token 重取。
     */
    @JvmStatic
    fun getGalleryDetail(
        gid: Long,
        token: String,
        allComments: Boolean = false,
        forceRefresh: Boolean = false,
        cancelKey: EhCalls.Handle? = null,
    ): GalleryDetail {
        // 空 token 拼出来的 /g/gid// 一定 404，先换一个真 token。
        val usableToken = if (token.isBlank()) getGalleryToken(gid, token) ?: token else token

        val first = runCatching { fetchDetail(gid, usableToken, allComments, forceRefresh, cancelKey) }
        val failure = first.exceptionOrNull()
        if (failure !is EhSiteException || failure.kind != EhSiteErrorKind.GALLERY_DELETED) {
            return first.getOrThrow()
        }

        val probe = queryGdata(gid, usableToken)
        val renewed = retryTokenFor(probe, usableToken)
        if (renewed != null) {
            return fetchDetail(gid, renewed, allComments, forceRefresh = true, cancelKey = cancelKey)
        }
        // gdata 查不到 ≠ 已删除：只有它明确说没有，才把「已删除」透给用户，
        // 其余情况一律降级成「当前不可用」，避免误报。
        throw if (fallbackKindFor(probe) == EhSiteErrorKind.GALLERY_DELETED) {
            failure
        } else {
            EhSiteException(EhSiteErrorKind.GALLERY_UNAVAILABLE, "画廊当前不可用", failure.url)
        }
    }

    /** gdata 的三种结论：还活着（新 token）/ 确认不存在 / 查询本身失败。 */
    internal sealed interface GdataResult {
        data class Alive(val token: String) : GdataResult
        data object NotFound : GdataResult
        data object Failed : GdataResult
    }

    /** 用 gdata 复核；任何异常都归为 [GdataResult.Failed]，不覆盖调用方原本的错误。 */
    internal fun queryGdata(gid: Long, token: String): GdataResult = try {
        val meta = gdataMetadata(gid, token)
        val fresh = meta?.getString("token")
        if (fresh.isNullOrBlank()) GdataResult.NotFound else GdataResult.Alive(fresh)
    } catch (t: Throwable) {
        GdataResult.Failed
    }

    /** 404 之后要不要换 token 重取：只有「gdata 说活着 + token 确实变了」才值得再发一次。 */
    internal fun retryTokenFor(result: GdataResult, effectiveToken: String): String? =
        (result as? GdataResult.Alive)?.token?.takeIf { it != effectiveToken }

    /** 复核后给用户的结论：明确不存在才算删除，其余降级为「不可用」。 */
    internal fun fallbackKindFor(result: GdataResult): EhSiteErrorKind =
        if (result is GdataResult.NotFound) EhSiteErrorKind.GALLERY_DELETED else EhSiteErrorKind.GALLERY_UNAVAILABLE

    /** 200 的「不可用」页同样先换 token 再试一次。 */
    private fun fetchDetail(
        gid: Long,
        token: String,
        allComments: Boolean,
        forceRefresh: Boolean,
        cancelKey: EhCalls.Handle? = null,
    ): GalleryDetail {
        val url = galleryUrl(gid, token) + if (allComments) "?hc=1" else ""
        val body = fetchText(url, forceRefresh, cancelKey)
        if (looksUnavailable(body)) {
            val fresh = getGalleryToken(gid, token)
            if (!fresh.isNullOrBlank() && fresh != token) {
                return GalleryDetailParser.parse(fetchText(galleryUrl(gid, fresh), true, cancelKey))
            }
        }
        return GalleryDetailParser.parse(body)
    }

    /**
     * 正文里值得怀疑 token 过期的信号。
     *
     * 不用「响应长度 < 2000」之类的猜测：最小详情页本来就短（见 fixtures/detail_minimal.html），
     * 短响应交给解析器自己的错误模式判断更准。
     */
    internal fun looksUnavailable(body: String): Boolean =
        body.contains("This gallery is unavailable") || body.contains("sad panda", ignoreCase = true)

    /** 用 gdata 换新 token；任何异常都返回 null，不覆盖调用方原本的错误。 */
    @JvmStatic
    fun getGalleryToken(gid: Long, token: String): String? = gdataMetadata(gid, token)?.getString("token")

    private fun gdataMetadata(gid: Long, token: String): JSONObject? {
        val payload = """{"method":"gdata","gidlist":[[$gid,"$token"]],"namespace":1}"""
        val meta = JSONObject.parseObject(submitJson(EhUrl.getApiUrl(), payload))?.getJSONArray("gmetadata")
        if (meta == null || meta.size == 0) return null
        return meta.getJSONObject(0)
    }

    // ============================================================ 列表补全 / 榜单 / 收藏

    /** 用 api.php 批量补全（标签、页数、评分）。超过 25 个自动分批。 */
    @JvmStatic
    fun fillGalleryListByApi(list: List<GalleryInfo>, cancelKey: EhCalls.Handle? = null) {
        if (list.isEmpty()) return
        list.chunked(API_BATCH_LIMIT).forEach { chunk ->
            val gidList = chunk.joinToString(",") { "[" + it.gid + ",\"" + it.token + "\"]" }
            val payload = "{\"method\":\"gdata\",\"gidlist\":[" + gidList + "],\"namespace\":1}"
            GalleryApiParser.parse(submitJson(EhUrl.getApiUrl(), payload, cancelKey), chunk)
        }
    }

    /** 同上，但失败不抛出，只记一条日志。 */
    @JvmStatic
    fun fillGalleryListByApiBestEffort(list: List<GalleryInfo>, cancelKey: EhCalls.Handle? = null) {
        runCatching { fillGalleryListByApi(list, cancelKey) }
            .onFailure { NetLog.log("API", EhUrl.getApiUrl(), 0, 0, 0, "gdata 补全失败：${it.message}") }
    }

    /** 预览集（阅读器页地址的来源）。 */
    @JvmStatic
    fun getPreviewSet(gid: Long, token: String) = getGalleryDetail(gid, token).previewSet

    /** 排行榜总览页。 */
    @JvmStatic
    fun getTopList(url: String) = TopListParser.parse(fetchText(url))

    /** 排行榜首页：7 类 × 4 个时段一次拿全。 */
    @JvmStatic
    fun getTopListPage(cancelKey: EhCalls.Handle? = null): EhTopListDetail =
        TopListParser.parse(fetchText(siteHost() + "toplist.php", cancelKey = cancelKey))

    /**
     * 排行榜分页列表 `toplist.php?tl=<tl>&p=<page>`。
     *
     * 里站经常在未登录/会话失效时返回提醒页或 sad panda；两站榜单数据一致，
     * 所以失败或空结果时退回表站再取一次。
     */
    @JvmStatic
    fun getRankList(
        tl: Int,
        page: Int,
        forceRefresh: Boolean = false,
        enrichTags: Boolean = false,
        cancelKey: EhCalls.Handle? = null,
    ): RankListParser.Result {
        val query = "toplist.php?tl=$tl&p=$page"
        val result = runCatching {
            RankListParser.parse(fetchText(siteHost() + query, forceRefresh, cancelKey))
        }.getOrElse { error ->
            if (error is InterruptedIOException) throw error
            if (!Settings.isExHentai) throw error
            NetLog.log("RETRY", query, 0, 0, 0, "里站榜单失败，退回表站")
            RankListParser.parse(fetchText(EhUrl.HOST_E + query, forceRefresh, cancelKey))
        }
            .let { parsed ->
                if (parsed.items.isNotEmpty() || !Settings.isExHentai) parsed
                else RankListParser.parse(fetchText(EhUrl.HOST_E + query, forceRefresh))
            }

        if (enrichTags) {
            // 紧凑榜单不带标签，用 gdata 补；已经缓存过的 gid 不再请求。
            val unknown = result.items.filter { RankTagCache.get(it.gid) == null }
            if (unknown.isNotEmpty()) fillGalleryListByApiBestEffort(unknown)
            result.items.forEach { info ->
                RankTagCache.get(info.gid)?.let { tags -> info.simpleTags = tags.toTypedArray() }
                info.simpleTags?.let { tags -> RankTagCache.put(info.gid, tags.toList()) }
            }
        }
        return result
    }

    /** 种子列表。 */
    @JvmStatic
    fun getTorrentList(gid: Long, token: String) =
        TorrentParser.parse(fetchText(galleryUrl(gid, token) + "?torrents=1"))

    /** 首页顶部的站内公告（事件横幅的 HTML）。 */
    @JvmStatic
    fun getEhNews(): String = EhEventParse.parse(fetchText(homeUrl()))

    /** 归档包列表。 */
    @JvmStatic
    fun getArchiveList(gid: Long, token: String) =
        ArchiveParser.parse(fetchText("${siteHost()}archiver.php?gid=$gid&token=$token"))

    /** 收藏夹；favCat 取 0..9，翻页用游标。 */
    @JvmStatic
    fun getFavourites(
        favCat: Int,
        nextHref: String? = null,
        cancelKey: EhCalls.Handle? = null,
    ): FavoritesParser.Result {
        val url = when {
            !nextHref.isNullOrBlank() && nextHref.startsWith("http") -> nextHref
            !nextHref.isNullOrBlank() -> siteHost() + nextHref.trimStart('/')
            else -> siteHost() + "favorites.php?favcat=" + favCat.coerceIn(0, 9)
        }
        return FavoritesParser.parse(fetchText(url, cancelKey = cancelKey))
    }

    /** 批量收藏：站点没有批量接口，逐个提交，返回成功数。 */
    @JvmStatic
    fun addFavoritesRange(gids: LongArray, tokens: Array<String>, dstCat: Int): Int {
        var succeeded = 0
        gids.forEachIndexed { index, gid ->
            val token = tokens.getOrNull(index) ?: return@forEachIndexed
            if (runCatching { addFavorites(gid, token, dstCat) }.getOrDefault(false)) succeeded++
        }
        return succeeded
    }

    /** 加入收藏（dstCat 0..9）或移出（-1）。 */
    @JvmStatic
    fun addFavorites(gid: Long, token: String, dstCat: Int, note: String? = null): Boolean {
        val category = when {
            dstCat == -1 -> "favdel"
            dstCat in 0..9 -> dstCat.toString()
            else -> throw IllegalArgumentException("Invalid dstCat: $dstCat")
        }
        val form = FormBody.Builder()
            .add("favcat", category)
            .add("favnote", note ?: "")
            .add("submit", "Apply Changes")
            .add("update", "1")
            .build()
        val body = submitForm("${siteHost()}gallerypopups.php?gid=$gid&t=$token&act=addfav", form)
        // 成功返回的是「已收藏」弹窗页，失败会带 Failed 文案。
        return body.isNotBlank() && !body.contains("Failed")
    }

    // ============================================================ 评论 / 标签

    /** 发表或编辑评论（editId 为 null 时新建），返回站点渲染后的评论列表。 */
    @JvmStatic
    @JvmOverloads
    fun commentGallery(
        gid: Long,
        token: String,
        comment: String,
        editId: Long? = null,
    ): GalleryCommentList {
        val form = FormBody.Builder()
        if (editId == null) {
            form.add("commenttext_new", comment)
        } else {
            form.add("commenttext_edit", comment)
            form.add("edit_comment", editId.toString())
        }
        val document = Jsoup.parse(submitForm(galleryUrl(gid, token), form.build()))
        document.select("#chd + p").firstOrNull()?.text()?.takeIf { it.isNotBlank() }?.let {
            throw EhException(it)
        }
        return GalleryDetailParser.parseComments(document)
    }

    /** 评论投票：1 赞 / -1 踩 / 0 取消。 */
    @JvmStatic
    fun voteComment(
        apiUid: Long,
        apiKey: String,
        gid: Long,
        token: String,
        commentId: Long,
        vote: Int,
    ): VoteCommentParser.Result {
        val payload = JSONObject()
        payload["method"] = "votecomment"
        payload["apiuid"] = apiUid
        payload["apikey"] = apiKey
        payload["gid"] = gid
        payload["token"] = token
        payload["comment_id"] = commentId
        payload["comment_vote"] = vote
        return VoteCommentParser.parse(submitJson(siteHost() + "api.php", payload.toString()), vote)
    }

    /** 某一页预览缩略图（每页 20 张）与总页数。 */
    @JvmStatic
    fun getPreviewPage(
        gid: Long,
        token: String,
        page: Int,
        cancelKey: EhCalls.Handle? = null,
    ): PreviewPage {
        val body = fetchText(galleryUrl(gid, token) + "?p=$page", cancelKey = cancelKey)
        val previews = GalleryDetailParser.parsePreviewSet(body)
        val pages = GalleryDetailParser.parsePreviewPages(body)
        return PreviewPage((0 until previews.size()).map { previews.getGalleryPreview(gid, it) }, pages)
    }

    /** 关注标签列表（需要登录）。 */
    fun getWatchedList(): List<UserTag> = MyTagLitParser.parse(fetchText(siteHost() + "mytags"))

    /** 添加关注标签；返回添加后的完整列表。 */
    fun addWatchedTag(
        tagName: String,
        watched: Boolean = true,
        hidden: Boolean = false,
        color: String? = null,
    ): List<UserTag> {
        val form = FormBody.Builder()
            .add("usertag_action", "add")
            .add("tagname_new", tagName)
            .apply {
                // 站点表单里 watch / hide 二选一。
                if (watched) {
                    add("tagwatch_new", "on")
                } else if (hidden) {
                    add("taghide_new", "on")
                }
            }
            .add("tagcolor_new", color ?: "")
            .add("tagweight_new", "10")
            .add("usertag_target", "0")
            .build()
        return parseWatchedResponse(submitForm(siteHost() + "mytags", form))
    }

    /** 删除关注标签（mass 模式）；返回删除后的完整列表。 */
    fun deleteWatchedTag(userTagId: String, weight: Int): List<UserTag> {
        val form = FormBody.Builder()
            .add("usertag_action", "mass")
            .add("tagname_new", "")
            .add("tagcolor_new", "")
            .add("tagweight_new", weight.toString())
            .add("modify_usertags[]", userTagId.removePrefix("usertag_"))
            .add("usertag_target", "0")
            .build()
        return parseWatchedResponse(submitForm(siteHost() + "mytags", form))
    }

    /** 站点拒收标签时不会给 `#usertags_outer`，用这一点区分成功与失败。 */
    private fun parseWatchedResponse(body: String): List<UserTag> {
        if (!body.contains("usertags_outer")) {
            throw EhException("标签可能已存在，或格式不符合要求（需要 namespace:name）")
        }
        return MyTagLitParser.parse(body)
    }

    // ============================================================ 标签数据库

    /**
     * EhTagTranslation 的发布产物。`db.text.json.gz` 比未压缩的小得多，
     * 所以优先 gz 源；最后的 raw 源没有压缩，作为兜底。
     */
    private val TAG_DB_SOURCES = listOf(
        "https://cdn.jsdelivr.net/gh/EhTagTranslation/DatabaseReleases@master/db.text.json.gz",
        "https://gcore.jsdelivr.net/gh/EhTagTranslation/DatabaseReleases@master/db.text.json.gz",
        "https://raw.githubusercontent.com/EhTagTranslation/DatabaseReleases/master/db.text.json",
    )

    /**
     * 只发 HEAD 取版本标记（ETag / Last-Modified）。
     *
     * 用来避免「开关关掉再打开」时白拉 4 MB：标记没变就说明服务端没更新。
     * 取不到（网络失败 / 服务端不给标记）返回 null，调用方按「有更新」处理。
     */
    fun fetchTagDatabaseStamp(): String? {
        for (url in TAG_DB_SOURCES) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .header(IMAGE_UA_HEADER, EhHttp.userAgent)
                    .build()
                EhHttp.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val raw = listOfNotNull(response.header("ETag"), response.header("Last-Modified"))
                        .joinToString("|")
                    if (raw.isNotEmpty()) return raw + "@" + url
                }
            } catch (e: Exception) {
                NetLog.log("HEAD", url, 0, 0, 0, "取标签库标记失败：${e.javaClass.simpleName}: ${e.message}")
            }
        }
        return null
    }

    /** 拉标签库全文（UTF-8 JSON），按源顺序重试。 */
    fun fetchTagDatabase(onProgress: (Float) -> Unit): String {
        var last: Exception? = null
        for (url in TAG_DB_SOURCES) {
            try {
                return fetchTagDatabaseFrom(url, onProgress)
            } catch (e: Exception) {
                last = e
                NetLog.log("GET-TAGDB", url, 0, 0, 0, "${e.javaClass.simpleName}: ${e.message}")
            }
        }
        throw last ?: IOException("tag database: no reachable source")
    }

    private fun fetchTagDatabaseFrom(url: String, onProgress: (Float) -> Unit): String {
        val gzipped = url.endsWith(".gz")
        // 4 MB 级的下载给足时间，别被默认的 callTimeout 掐掉。
        val client = if (gzipped) EhHttp.client.newBuilder().callTimeout(90, TimeUnit.SECONDS).build() else EhHttp.client
        val request = Request.Builder()
            .url(url)
            .header(IMAGE_UA_HEADER, EhHttp.userAgent)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpFailure(response, url)
            val body = response.body ?: throw IOException("empty body")
            val declared = body.contentLength()
            val sink = ByteArrayOutputStream(
                if (declared > 0) (declared * 3).toInt().coerceAtMost(64 * 1024 * 1024) else 1 shl 20,
            )
            val buffer = ByteArray(32 * 1024)
            val raw = body.byteStream()
            val input = if (gzipped) GZIPInputStream(raw, 32 * 1024) else raw
            input.use { stream ->
                var read: Int
                var done = 0L
                while (stream.read(buffer).also { read = it } != -1) {
                    sink.write(buffer, 0, read)
                    done += read
                    // gz 解压后的大小和 Content-Length 不可比，进度只在明文源上按字节报。
                    if (!gzipped && declared > 0) {
                        onProgress((done.toFloat() / declared).coerceIn(0f, 1f))
                    }
                }
            }
            onProgress(1f)
            return sink.toString("UTF-8")
        }
    }

    // ============================================================ 图片下载

    /** 下载原图字节（长按保存用）。 */
    @JvmStatic
    fun downloadImage(imageUrl: String): ByteArray {
        val started = System.currentTimeMillis()
        var code = -1
        var size = 0L
        var failure: String? = null
        try {
            val request = Request.Builder()
                .url(imageUrl)
                .header(IMAGE_UA_HEADER, EhHttp.userAgent)
                .build()
            EhHttp.client.newCall(request).execute().use { response ->
                code = response.code
                if (!response.isSuccessful) {
                    failure = "HTTP $code"
                    throw httpFailure(response, imageUrl)
                }
                val bytes = response.body?.bytes() ?: ByteArray(0)
                size = bytes.size.toLong()
                return bytes
            }
        } catch (t: Throwable) {
            if (failure == null) failure = t.message ?: t.javaClass.simpleName
            throw t
        } finally {
            NetLog.log("GET-IMG", imageUrl, code, System.currentTimeMillis() - started, size, failure)
        }
    }

    /** 逐块下载并回报 0f..1f，阅读器用它显示百分比。 */
    @JvmStatic
    fun downloadImageWithProgress(imageUrl: String, onProgress: (Float) -> Unit): ByteArray {
        val request = Request.Builder()
            .url(imageUrl)
            .header(IMAGE_UA_HEADER, EhHttp.userAgent)
            .build()
        EhHttp.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpFailure(response, imageUrl)
            val body = response.body ?: throw IOException("empty body")
            val declared = body.contentLength()
            val sink = ByteArrayOutputStream(
                if (declared > 0) declared.toInt().coerceAtMost(32 * 1024 * 1024) else 64 * 1024,
            )
            val buffer = ByteArray(16 * 1024)
            body.byteStream().use { stream ->
                var read: Int
                var done = 0L
                while (stream.read(buffer).also { read = it } != -1) {
                    sink.write(buffer, 0, read)
                    done += read
                    if (declared > 0) onProgress((done.toFloat() / declared).coerceIn(0f, 1f))
                }
            }
            onProgress(1f)
            return sink.toByteArray()
        }
    }

    // ============================== 未实现（当前无调用方，接入前需补实现）

    /**
     * 以下 4 个接口保留签名以对齐调用方契约，但**尚未实现**：
     * 当前仓库内没有调用方，一旦接入会抛 [UnsupportedOperationException]。
     */

    @JvmStatic
    fun addTag(gid: Long, token: String, tag: String): Boolean = notPorted("addTag")

    @JvmStatic
    fun downloadArchive(gid: Long, token: String, body: String): Boolean = notPorted("downloadArchive")

    @JvmStatic
    fun getArchiver(gid: Long, token: String): String = notPorted("getArchiver")

    @JvmStatic
    fun downloadArchiver(gid: Long, token: String, form: String): Boolean = notPorted("downloadArchiver")

    /**
     * core 层没有需要预热的全局状态，因此这里是空操作；过滤器与标签库在
     * shared 层按需懒加载，不依赖这个入口。
     */
    @JvmStatic
    fun initialize() {
        NetLog.log("INIT", "EhEngine", 0, 0, 0, "no-op：core 层无全局状态")
    }

    private fun <T> notPorted(name: String): T = throw UnsupportedOperationException("EhEngine.$name 尚未实现")

    // ============================================================ 传输层

    /**
     * 同 URL 的并发 GET 只发一次，其余等同一份结果（请求去重）。
     * POST 与下拉刷新不参与，避免副作用与旧结果。
     *
     * 取消只作用于「真正发请求」的那条分支：等待复用的调用方即使被取消，也不会把
     * 别人共用的那条 Call 掐掉，只是继续等它结束（最长 callTimeout）。
     *
     * internal 而非 private：core 的测试需要直接驱动这条阻塞路径来验证取消行为。
     */
    internal fun request(
        url: String,
        body: RequestBody?,
        forceRefresh: Boolean = false,
        cancelKey: EhCalls.Handle? = null,
    ): String {
        if (body != null || forceRefresh) return executeOnce(url, body, forceRefresh, cancelKey)

        val key = "GET " + url
        val mine = CompletableFuture<String>()
        val leader = pendingRequests.putIfAbsent(key, mine)
        if (leader != null) {
            NetLog.log("GET-DEDUP", url, 0, 0, 0, null)
            return try {
                leader.get()
            } catch (e: ExecutionException) {
                throw e.cause as? Throwable ?: e
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("interrupted while waiting for duplicate request", e)
            }
        }
        try {
            val result = executeOnce(url, body, forceRefresh, cancelKey)
            mine.complete(result)
            return result
        } catch (t: Throwable) {
            mine.completeExceptionally(t)
            throw t
        } finally {
            pendingRequests.remove(key, mine)
        }
    }

    private val pendingRequests = ConcurrentHashMap<String, CompletableFuture<String>>()

    private fun executeOnce(
        url: String,
        body: RequestBody?,
        forceRefresh: Boolean,
        cancelKey: EhCalls.Handle?,
    ): String {
        if (forceRefresh) {
            // 下拉刷新：先失效这个 URL（或该画廊的分页前缀）的原始响应缓存。
            HttpCacheBridgeHolder.removePrefix(HttpCacheKeys.scoped(refreshPrefix(url)))
        }

        val policy = cachePolicyFor(url, forceRefresh)
        val started = System.currentTimeMillis()
        var code = -1
        var size = 0L
        var failure: String? = null
        var servedFromCache: String? = null
        var classified: String? = null
        try {
            val builder = Request.Builder()
                .url(url)
                .header(IMAGE_UA_HEADER, EhHttp.userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            if (body == null && policy.maxAgeMillis > 0) {
                builder.header("X-Eh-Cache-Max-Age", policy.maxAgeMillis.toString())
            }
            if (body != null) builder.post(body)

            val call = EhHttp.client.newCall(builder.build())
            // 先登记再 execute：协程侧取消（含 withTimeout）会走到 Call.cancel()，
            // 阻塞中的 socket 读立刻抛 IOException("Canceled")。
            cancelKey?.let { EhCalls.attach(it, call) }
            try {
                call.execute().use { response ->
                    code = response.code
                    servedFromCache = response.header("X-Eh-HttpCache")
                    // 分类头由 HttpCacheInterceptor 打上（NORMAL / LOGIN_REQUIRED / …），
                    // 记进 netlog，导日志时不必再猜「空页面」到底是哪种。
                    classified = response.header(ResponseClassifier.HEADER)
                    val text = response.body?.string().orEmpty()
                    size = text.length.toLong()

                    if (code == 404 && isGonePage(text)) {
                        failure = "GALLERY_DELETED"
                        // 旧版本可能把负页写进缓存，顺手清掉，免得一次残留钉住几小时。
                        HttpCacheBridgeHolder.removePrefix(HttpCacheKeys.scoped(url))
                        throw EhSiteException(EhSiteErrorKind.GALLERY_DELETED, "画廊已下架", url)
                    }
                    if (code !in 200..299) {
                        failure = "HTTP $code"
                        if (code == 403) {
                            throw EhSiteException(EhSiteErrorKind.CLOUDFLARE, "Cloudflare 拦截（403）", url)
                        }
                        throw httpFailure(response, url)
                    }
                    classifySiteError(text, url)?.let {
                        failure = it.message
                        HttpCacheBridgeHolder.removePrefix(HttpCacheKeys.scoped(url))
                        throw it
                    }
                    return text
                }
            } finally {
                cancelKey?.let { EhCalls.detach(it) }
            }
        } catch (t: Throwable) {
            if (failure == null) failure = t.message ?: t.javaClass.simpleName
            throw t
        } finally {
            val method = when {
                servedFromCache == "HIT" -> "GET-CACHE"
                body == null -> "GET"
                else -> "POST"
            }
            NetLog.log(method, url, code, System.currentTimeMillis() - started, size, failure, classified)
        }
    }

    /** 刷新时按画廊整体失效：分页 URL 的前缀一直退到 `?p=` 之前。 */
    private fun refreshPrefix(url: String): String =
        if (url.contains("/g/") && url.contains("?p=")) url.substringBefore("?p=") else url

    /** 404 响应是否其实是「画廊没了」。 */
    private fun isGonePage(text: String): Boolean =
        text.contains("Gallery not found", ignoreCase = true) ||
            text.contains("This gallery has been removed", ignoreCase = true) ||
            text.contains("画廊已删除", ignoreCase = true)

    /** 200 响应里的站点级异常页：空响应（Sad Panda）与 IP 被封。 */
    private fun classifySiteError(text: String, url: String): EhSiteException? {
        val trimmed = text.trim()
        return when {
            trimmed.isEmpty() -> EhSiteException(EhSiteErrorKind.BLANK_BODY, "空响应（Sad Panda）", url)
            trimmed.startsWith("Your IP address") || trimmed.startsWith("This IP address") ->
                EhSiteException(EhSiteErrorKind.BANNED, trimmed.take(200), url)
            else -> null
        }
    }

    /**
     * 带状态码与 `Retry-After` 的 HTTP 错误，交给上层映射成 EhError。
     *
     * `Retry-After` 可能是数字秒，也可能是 HTTP-date（Cloudflare 常发），
     * 统一由 [RetryAfter] 换算；日期形式在转成秒时向下取整。
     */
    private fun httpFailure(response: Response, url: String): HttpStatusException =
        HttpStatusException(
            code = response.code,
            retryAfterSeconds = RetryAfter.parseMillis(response.header("Retry-After"))?.div(1000),
            url = url,
        )

    // ============================================================ URL 工具

    private fun siteHost(): String = if (Settings.isExHentai) EhUrl.HOST_EX else EhUrl.HOST_E

    private fun homeUrl(): String = siteHost()

    private fun galleryUrl(gid: Long, token: String): String = "${siteHost()}g/$gid/$token/"

    private fun absoluteUrl(pageUrl: String): String =
        if (pageUrl.startsWith("http")) pageUrl else siteHost() + pageUrl.trimStart('/')

    private fun fetchText(
        url: String,
        forceRefresh: Boolean = false,
        cancelKey: EhCalls.Handle? = null,
    ): String = request(url, null, forceRefresh, cancelKey)

    private fun submitForm(
        url: String,
        form: FormBody,
        cancelKey: EhCalls.Handle? = null,
    ): String = request(url, form, false, cancelKey)

    private fun submitJson(
        url: String,
        payload: String,
        cancelKey: EhCalls.Handle? = null,
    ): String = request(url, payload.toRequestBody("application/json".toMediaType()), false, cancelKey)

    private const val PAGING_CONCURRENCY = 4
}
