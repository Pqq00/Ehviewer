// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.util.AppLog
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * 原始 HTTP 响应缓存（按 URL 存 body + 头 + 过期时间）。
 *
 * 与 shared 层的 `GalleryCache`（解析后的模型）分工：
 *  - 这里缓存「网络原始字节 + 头 + 过期时间」，命中时直接在 OkHttp 层返回，不解析、不耗流量；
 *  - `GalleryCache` 缓存「列表/详情模型」，用于首屏立即渲染。
 */
data class CachedHttpResponse(
    val body: ByteArray,
    val contentType: String?,
    val expiresAt: Long,
)

/** 平台实现：Android 用 SQLite，桌面/测试用内存。 */
interface HttpCacheBridge {
    fun get(url: String, now: Long): CachedHttpResponse?
    fun put(url: String, response: CachedHttpResponse)
    fun removePrefix(prefix: String)
    fun clear()
}

object HttpCacheBridgeHolder {
    @Volatile
    var bridge: HttpCacheBridge? = null

    fun removePrefix(prefix: String) {
        bridge?.removePrefix(prefix)
    }

    fun clear() {
        bridge?.clear()
    }
}

/** 请求级缓存策略；值为 0 表示不读不写。 */
enum class HttpCachePolicy(val maxAgeMillis: Long) {
    NONE(0L),
    PAGE(60L * 60 * 1000),            // 首页/收藏/排行/搜索
    DETAIL(6L * 60 * 60 * 1000),      // 画廊详情
    PAGE_LIST(24L * 60 * 60 * 1000),  // 页地址列表 / 图片页
}

/**
 * 按 URL 推断缓存策略（EhEngine 内部使用）。
 *
 * `forceRefresh` 时返回 [HttpCachePolicy.NONE]，让拦截器完全绕过（下拉刷新）。
 */
fun cachePolicyFor(url: String, forceRefresh: Boolean): HttpCachePolicy {
    if (forceRefresh) return HttpCachePolicy.NONE
    return when {
        url.contains("api.php") -> HttpCachePolicy.NONE
        url.contains("/s/") -> HttpCachePolicy.PAGE_LIST
        url.contains("/g/") && url.contains("?p=") -> HttpCachePolicy.PAGE_LIST
        url.contains("/g/") -> HttpCachePolicy.DETAIL
        url.contains("favorites.php") -> HttpCachePolicy.PAGE
        url.contains("toplist.php") -> HttpCachePolicy.PAGE
        else -> HttpCachePolicy.PAGE
    }
}

private const val HEADER_MAX_AGE = "X-Eh-Cache-Max-Age"

/**
 * 原始响应缓存的 key。
 *
 * 缓存的是**站点 + 登录态 + URL** 三者组合，不能只用 URL：
 *  - 表站和里站同一个 `/g/1/abc/` 地址返回的是不同内容（里站多一类分类），
 *    只用 URL 会让从里站拿到的页在切回表站后仍被复用；
 *  - 未登录与已登录看到的首页/详情也不同（收藏数、标签可见性），
 *    登出后还能命中登录时的缓存同样会串内容。
 *
 * 前缀固定为 `e|out|` 这种短串，`removePrefix` 依旧按前缀批量删。
 */
internal object HttpCacheKeys {

    fun scoped(url: String): String {
        val site = if (Settings.isExHentai) "ex" else "e"
        val signed = if (EhCookieJar.has(EhCookieJar.KEY_MEMBER_ID) && EhCookieJar.has(EhCookieJar.KEY_PASS_HASH)) {
            "in"
        } else {
            "out"
        }
        return "$site|$signed|$url"
    }
}

/**
 * OkHttp 拦截器：按 `X-Eh-Cache-Max-Age` 头读写 [HttpCacheBridgeHolder.bridge]。
 *
 * 命中时返回一个合成的 200 响应，并带 `X-Eh-HttpCache: HIT`，方便 NetLog 标注；
 * 两种响应都会带 `X-Eh-Classified`，标明这份内容被归类成什么（见 [ResponseClassifier]）。
 */
internal class HttpCacheInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)

        val maxAge = request.header(HEADER_MAX_AGE)?.toLongOrNull() ?: 0L
        val bridge = HttpCacheBridgeHolder.bridge
        if (maxAge <= 0L || bridge == null) {
            return chain.proceed(request.newBuilder().removeHeader(HEADER_MAX_AGE).build())
        }

        val url = request.url.toString()
        val key = HttpCacheKeys.scoped(url)
        val now = System.currentTimeMillis()
        val cached = runCatching { bridge.get(key, now) }.getOrNull()
        if (cached != null) {
            // 命中不打日志：一次阅读里同一页会被问上千次，HIT 行占导出日志 30%，
            // 而 netlog 里的 GET-CACHE 已经能看出发过哪些请求。只保留 MISS/SKIP。
            val type = cached.contentType?.toMediaTypeOrNull()
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK (cache)")
                .header("X-Eh-HttpCache", "HIT")
                // 缓存里只会有 NORMAL，写上让上层逻辑不必区分"命中"与"网络"。
                .header(ResponseClassifier.HEADER, ResponseClass.NORMAL.name)
                .apply { cached.contentType?.let { header("Content-Type", it) } }
                .body(cached.body.toResponseBody(type))
                .build()
        }

        val clean = request.newBuilder().removeHeader(HEADER_MAX_AGE).build()
        val response = chain.proceed(clean)
        val body = response.body
        if (body == null || (response.code != 200 && response.code !in 400..599)) {
            return response
        }
        val bytes = body.bytes()
        val contentType = body.contentType()?.toString()
        val classified = ResponseClassifier.classify(response.code, bytes, contentType)
        if (response.code == 200) {
            if (ResponseClassifier.isCacheable(classified) &&
                bytes.isNotEmpty() &&
                response.header("Cache-Control")?.contains("no-store", true) != true
            ) {
                runCatching {
                    bridge.put(key, CachedHttpResponse(bytes, contentType, now + maxAge))
                    AppLog.d("HttpCache", "MISS $url (${bytes.size}B, ttl=${maxAge}ms)")
                }
            } else {
                // 登录提示 / sad panda / 维护页等非内容页：钉住 6–24 小时会一直显示错误页。
                AppLog.d("HttpCache", "SKIP $url (${classified.name})")
            }
        }
        return response.newBuilder()
            .header("X-Eh-HttpCache", "MISS")
            .header(ResponseClassifier.HEADER, classified.name)
            .body(bytes.toResponseBody(contentType?.toMediaTypeOrNull()))
            .build()
    }
}
