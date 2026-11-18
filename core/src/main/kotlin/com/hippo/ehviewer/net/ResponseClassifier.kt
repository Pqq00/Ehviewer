// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

/**
 * 站点响应的语义分类。
 *
 * 存在的意义只有一个：**决定这份响应能不能进 HTTP 缓存**。E 站会用 200 返回一堆
 * 非内容页（cookie 失效的登录提示、里站 sad panda、IP 限流、维护页、Cloudflare 挑战），
 * 把这些当成正常内容缓存 6–24 小时，用户会一直看到"已下架/请登录"，只能靠清缓存恢复。
 */
enum class ResponseClass {
    /** 正常内容，只有它才允许落缓存。 */
    NORMAL,

    /** Cookie 失效 / 未登录。 */
    LOGIN_REQUIRED,

    /** 里站权限不足（sad panda）。 */
    SAD_PANDA,

    /** Cloudflare 挑战 / 拦截页。 */
    CLOUDFLARE,

    /** IP 被临时封禁（pageload 限流）。 */
    BANNED,

    /** 画廊已删除 / 不可用。 */
    REMOVED,

    /** 站点维护。 */
    MAINTENANCE,

    /** 5xx。 */
    SERVER_ERROR,

    /** 无法归类。同样不落缓存 —— 宁可多走一次网络。 */
    UNKNOWN,
}

/**
 * 把一次 HTTP 响应归类到 [ResponseClass]。
 *
 * 只看状态码 + 前缀标记，不做 HTML 解析：这里跑在 OkHttp 拦截器里，每个响应都要过一遍，
 * 必须足够便宜。标记字符串取自站点实际返回的页面文案（与 `EhAuth.classifyExResponse`
 * 和 `EhEngine.classifySiteError` 保持同一批措辞）。
 */
object ResponseClassifier {

    /** 写进响应的头，调用方/NetLog 可以据此判断这份响应是什么。 */
    const val HEADER = "X-Eh-Classified"

    private val CLOUDFLARE_MARKERS = listOf(
        "Just a moment",
        "__cf_chl",
        "cf-browser-verification",
        "Attention Required",
        "cloudflare",
    )

    private val LOGIN_MARKERS = listOf(
        "You are not logged in",
        "you are not logged in",
        "This page requires you to be logged in",
        "Please log in to continue",
        "Your login has expired",
    )

    private val BANNED_MARKERS = listOf(
        "Your IP address has been temporarily banned",
        "This IP address has been temporarily banned",
    )

    private val REMOVED_MARKERS = listOf(
        "Gallery not found",
        "This gallery has been removed",
        "This gallery is unavailable",
    )

    private val MAINTENANCE_MARKERS = listOf(
        "pining for the fjords",
        "We are currently performing maintenance",
        "Site maintenance",
        "temporarily unavailable for maintenance",
    )

    /**
     * @param code HTTP 状态码
     * @param body 响应字节（可为 null，例如 204）
     * @param contentType `Content-Type` 头，用于跳过二进制内容
     */
    fun classify(code: Int, body: ByteArray?, contentType: String?): ResponseClass {
        if (code == 403 || code == 429 || code == 503) {
            // Cloudflare 挑战与限流都落在这三个码上；正文是 HTML，再看一眼标记更稳。
            if (containsAny(decodeText(body, contentType), CLOUDFLARE_MARKERS)) return ResponseClass.CLOUDFLARE
            if (code == 503) return ResponseClass.MAINTENANCE
            return ResponseClass.UNKNOWN
        }
        if (code in 500..599) return ResponseClass.SERVER_ERROR
        if (code != 200) return ResponseClass.UNKNOWN

        if (body == null || body.isEmpty()) return ResponseClass.UNKNOWN
        // 图片等二进制响应没有可扫的文案，200 + 有内容就按正常内容处理（可缓存）。
        if (!isTextual(contentType)) return ResponseClass.NORMAL

        val text = decodeText(body, contentType)
        return when {
            containsAny(text, CLOUDFLARE_MARKERS) -> ResponseClass.CLOUDFLARE
            containsAny(text, BANNED_MARKERS) || text.trimStart().startsWith("Your IP address") -> ResponseClass.BANNED
            containsAny(text, MAINTENANCE_MARKERS) -> ResponseClass.MAINTENANCE
            text.contains("sad panda", ignoreCase = true) -> ResponseClass.SAD_PANDA
            containsAny(text, LOGIN_MARKERS) -> ResponseClass.LOGIN_REQUIRED
            containsAny(text, REMOVED_MARKERS) -> ResponseClass.REMOVED
            else -> ResponseClass.NORMAL
        }
    }

    /** 只有 [ResponseClass.NORMAL] 值得写进原始响应缓存。 */
    fun isCacheable(cls: ResponseClass): Boolean = cls == ResponseClass.NORMAL

    private fun containsAny(text: String, markers: List<String>): Boolean =
        markers.any { text.contains(it) }

    /**
     * 只解码文本类响应。图片字节里偶尔会出现类似标记的片段，拿它做子串匹配会误判成
     * "维护页"并丢掉本来可以缓存的缩略图。
     */
    private fun decodeText(body: ByteArray?, contentType: String?): String {
        if (body == null || body.isEmpty()) return ""
        if (body.size > MAX_SCAN_BYTES) return ""
        if (!isTextual(contentType)) return ""
        return String(body, Charsets.UTF_8)
    }

    /** `null` 视为文本（有的响应不带 Content-Type，页面文案仍值得扫）。 */
    private fun isTextual(contentType: String?): Boolean {
        val type = contentType?.lowercase() ?: return true
        return type.startsWith("text/") ||
            type.contains("json") ||
            type.contains("xml") ||
            type.contains("javascript") ||
            type.contains("xhtml")
    }

    /** 超过这个体积的正文不做扫描（正常页面/接口都远小于它）。 */
    private const val MAX_SCAN_BYTES = 4 * 1024 * 1024
}
