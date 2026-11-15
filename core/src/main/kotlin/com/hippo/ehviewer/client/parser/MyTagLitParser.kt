// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.parser

import com.hippo.ehviewer.client.data.UserTag
import com.hippo.ehviewer.client.exception.EhException
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.regex.Pattern

/**
 * 「关注标签」页（mytags）解析器。
 *
 * 每个标签是 `#usertags_outer` 下的一个子元素（第 0 个是表头，跳过），元素 id 形如
 * `usertag_3437`。标签名与两个开关、颜色、权重分别挂在 id 为
 * `tagpreview_3437` / `tagwatch_3437` / `taghide_3437` / `tagcolor_3437` / `tagweight_3437`
 * 的控件上，所以取字段时要先把 `_<数字>` 后缀从自身 id 里切出来。
 */
object MyTagLitParser {

    private const val OUTER_ID = "usertags_outer"
    private const val ID_PREFIX = "usertag"

    private val ERROR_BLOCK = Pattern.compile("<div class=\"d\">\n<p>([^<]+)</p>")

    fun parse(body: String): List<UserTag> {
        val error = ERROR_BLOCK.matcher(body)
        if (error.find()) {
            throw EhException(error.group(1))
        }
        // 找不到容器（未登录 / 站点改版）时返回空表：调用方一直按「没有标签」处理。
        val outer = Jsoup.parse(body).getElementById(OUTER_ID) ?: return emptyList()
        return outer.children().drop(1).mapNotNull(::parseTag)
    }

    private fun parseTag(holder: Element): UserTag? {
        val holderId = holder.id()
        if (holderId.isEmpty()) {
            return null
        }
        val suffix = holderId.removePrefix(ID_PREFIX)

        val preview = holder.getElementById("tagpreview$suffix")
        val tag = UserTag()
        tag.userTagId = holderId
        tag.tagName = preview?.attr("title") ?: preview?.text()
        tag.watched = isChecked(holder.getElementById("tagwatch$suffix"))
        tag.hidden = isChecked(holder.getElementById("taghide$suffix"))
        tag.color = holder.getElementById("tagcolor$suffix")?.attr("placeholder")
        tag.tagWeight = holder.getElementById("tagweight$suffix")?.attr("value").orEmpty().toIntOrNull() ?: 0
        return tag
    }

    private fun isChecked(input: Element?): Boolean =
        input != null && (input.hasAttr("checked") || input.attr("checked") == "checked")
}
