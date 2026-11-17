// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

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
 * OkHttp 拦截器：按 `X-Eh-Cache-Max-Age` 头读写 [HttpCacheBridgeHolder.bridge]。
 *
 * 命中时返回一个合成的 200 响应，并带 `X-Eh-HttpCache: HIT`，方便 NetLog 标注。
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
        val now = System.currentTimeMillis()
        val cached = runCatching { bridge.get(url, now) }.getOrNull()
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
                .apply { cached.contentType?.let { header("Content-Type", it) } }
                .body(cached.body.toResponseBody(type))
                .build()
        }

        val clean = request.newBuilder().removeHeader(HEADER_MAX_AGE).build()
        val response = chain.proceed(clean)
        if (response.code == 200) {
            val body = response.body ?: return response
            val bytes = body.bytes()
            val contentType = body.contentType()?.toString()
            if (isNegativePage(bytes)) {
                // 已删除/不可用/维护页会被钉住 DETAIL 6 小时，宁可每次都走网络。
                AppLog.d("HttpCache", "SKIP $url (negative page)")
            } else if (bytes.isNotEmpty() && response.header("Cache-Control")?.contains("no-store", true) != true) {
                runCatching {
                    bridge.put(url, CachedHttpResponse(bytes, contentType, now + maxAge))
                    AppLog.d("HttpCache", "MISS $url (${bytes.size}B, ttl=${maxAge}ms)")
                }
            }
            return response.newBuilder()
                .header("X-Eh-HttpCache", "MISS")
                .body(bytes.toResponseBody(contentType?.toMediaTypeOrNull()))
                .build()
        }
        return response
    }

    /** 站点返回的"负页"：画廊已删除 / 不可用 / 维护中。 */
    private fun isNegativePage(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val text = String(bytes, Charsets.UTF_8)
        return text.contains("Gallery not found") ||
            text.contains("This gallery has been removed") ||
            text.contains("This gallery is unavailable") ||
            text.contains("pining for the fjords")
    }
}
