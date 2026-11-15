// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

/** 站内画廊链接的解析结果。 */
data class GalleryRef(
    val gid: Long,
    val token: String,
)

/** 文本里的一段链接（[start] 到 [end] 是它在原文里的位置，用于富文本注解）。 */
data class LinkSpan(
    val url: String,
    val start: Int,
    val end: Int,
)

/**
 * 识别 E-Hentai 画廊链接。
 *
 * 只认 `/g/<gid>/<token>` 这种**带 token** 的形态：详情页的后续请求（评论、预览、
 * 阅读地址）都要用 token 拼 `/g/<gid>/<token>/`，没有 token 的链接解析出来也只能失败，
 * 所以宁可返回 null 让它走浏览器。
 */
object GalleryLink {

    /** 评论/正文里可能出现的链接：http(s) 绝对地址，或是裸的站内域名地址。 */
    private val URL_PATTERN = Regex(
        """(?i)(?:https?://[^\s<>"'）】」，。；！？、]+|(?:www\.)?(?:e-hentai\.org|exhentai\.org)/[^\s<>"'）】」，。；！？、]*)""",
    )

    /** 去掉协议后匹配 `host/g/<gid>/<token>`；子域名（lofi. 等）也算。 */
    private val GALLERY_PATH = Regex(
        """^(?:[a-z0-9-]+\.)*(?:e-hentai\.org|exhentai\.org)/g/(\d+)/([0-9a-zA-Z]+)/?$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 解析单个链接；不是站内画廊链接时返回 null。
     *
     * 支持带/不带协议、带查询参数或锚点、结尾多一个斜杠，以及尾随标点（会被先裁掉）。
     */
    fun parse(raw: String): GalleryRef? {
        val cleaned = trimUrl(raw)
        if (cleaned.isEmpty()) return null
        val body = cleaned.substringAfter("://", cleaned)
        val path = body.substringBefore('?').substringBefore('#')
        val match = GALLERY_PATH.find(path) ?: return null
        val gid = match.groupValues[1].toLongOrNull() ?: return null
        val token = match.groupValues[2]
        if (gid <= 0L || token.isEmpty()) return null
        return GalleryRef(gid, token)
    }

    /** 找出文本里所有链接（可能包含站外链接，调用方自行决定怎么打开）。 */
    fun findAll(text: String): List<LinkSpan> {
        if (text.isEmpty()) return emptyList()
        return URL_PATTERN.findAll(text).mapNotNull { match ->
            val url = trimUrl(match.value)
            if (url.isEmpty()) null else LinkSpan(url, match.range.first, match.range.first + url.length)
        }.toList()
    }

    /** 文本里第一个可跳转的画廊链接。 */
    fun first(text: String): GalleryRef? =
        findAll(text).firstNotNullOfOrNull { parse(it.url) }

    /** 补全协议，交给系统浏览器时用。 */
    fun externalUrl(raw: String): String {
        val url = trimUrl(raw)
        return if (url.startsWith("http://", true) || url.startsWith("https://", true)) url
        else "https://$url"
    }

    /** 裁掉链接末尾容易混进来的标点/括号。 */
    private fun trimUrl(raw: String): String =
        raw.trim().trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '>', '"', '\'',
            '。', '，', '、', '；', '：', '！', '？', '）', '】', '」', '』')
}
