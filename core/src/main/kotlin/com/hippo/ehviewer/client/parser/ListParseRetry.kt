// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.parser

/**
 * 列表页解析出 0 条时的自愈判断。
 *
 * 站点有三种列表视图：紧凑 `.gltc`、缩略图 `.gltm`、扩展 `.glte`。解析器只认其中一种，
 * 碰上别的视图会得到「页面是列表页，但解析出 0 条」这种不报错的失败。
 * 三个纯函数把这件事拆开：判断是不是列表页、要不要重试、怎么改 URL 强制扩展视图。
 */
object ListParseRetry {

    /** 强调制参数：让站点返回解析器认识的扩展视图。 */
    const val FORCE_VIEW_PARAM = "inline_set=dm_l"

    private val LIST_MARKERS = listOf(
        "itg gltc",
        "itg gltm",
        "itg glte",
        "itg gld",
        "searchnav",
    )

    fun looksLikeListPage(body: String): Boolean =
        body.isNotEmpty() && LIST_MARKERS.any(body::contains)

    fun shouldRetry(body: String, parsedCount: Int): Boolean =
        parsedCount == 0 && looksLikeListPage(body)

    /** 追加 [param]；已有同名参数时原样返回。 */
    fun withInlineSet(url: String, param: String = FORCE_VIEW_PARAM): String {
        if (url.contains(param)) {
            return url
        }
        val separator = if (url.contains('?')) '&' else '?'
        return url + separator + param
    }
}
