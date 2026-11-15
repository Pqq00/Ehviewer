// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.parser

import com.hippo.ehviewer.client.data.GalleryInfo
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * 排行榜列表解析器（`toplist.php?tl=<tl>&p=<page>`）。
 *
 * 带 `tl`/`p` 参数时站点返回**紧凑列表**（`.itg.gltc` 表格），每行一个画廊，
 * 带封面、标题、页数、上传者，并且分页（`?p=N`），所以可以无限滚动加载。
 */
object RankListParser {

    private const val TABLE_SELECTOR = ".itg.gltc > tbody > tr"
    private const val PAGER_ROW_SELECTOR = ".ptt > tbody > tr"

    private val PAGE_PARAM = Regex("""p(?:age)?=(\d+)""")
    private val DIGITS = Regex("""(\d+)""")
    private val GALLERY_PATH = Regex("""/(?:g|mpv)/(\d+)/([0-9a-fA-F]+)""")

    class Result(
        val items: List<GalleryInfo>,
        /** 下一页页码（0-based），null 表示没有下一页。 */
        val nextPage: Int?,
        /** 总页数（站点显示 "1 / 20" 这类信息）。 */
        val totalPages: Int,
    )

    fun parse(body: String): Result {
        val document = Jsoup.parse(body)

        val items = document.select(TABLE_SELECTOR)
            // 表头行带 <th>，广告行只有一格；两者都不是榜单条目
            .filter { it.childrenSize() != 1 && it.select("th").isEmpty() }
            .mapNotNull(::parseRow)

        val pager = document.selectFirst(PAGER_ROW_SELECTOR)?.children()
        return Result(items, nextPage(pager), totalPages(pager))
    }

    /** 分页器最后一格是「Next」，从中取 `?p=N`；没有下一页则为 null。 */
    private fun nextPage(pager: Elements?): Int? =
        pager?.lastOrNull()
            ?.selectFirst("a")
            ?.attr("href")
            ?.let { PAGE_PARAM.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

    /** 分页器倒数第二格形如 "1 / 20"，取最后一个数字作为总页数。 */
    private fun totalPages(pager: Elements?): Int =
        pager?.takeIf { it.size >= 2 }
            ?.let { it[it.size - 2].text() }
            ?.let { DIGITS.findAll(it).lastOrNull()?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?: 0

    private fun parseRow(row: Element): GalleryInfo? {
        val anchor = row.selectFirst(".gl3c.glname > a") ?: return null
        val href = anchor.attr("href")
        val match = GALLERY_PATH.find(href) ?: return null
        val gid = match.groupValues[1].toLongOrNull() ?: return null

        val info = GalleryInfo()
        info.gid = gid
        info.token = match.groupValues[2]
        info.title = row.selectFirst(".glink")?.text()?.trim().orEmpty().ifBlank { href }
        info.thumb = thumbnail(row)
        info.pages = pages(row)
        info.uploader = row.selectFirst(".gl4c.glhide > div > a")?.text()?.trim()?.takeIf { it.isNotBlank() }
        info.category = categoryBit(row.selectFirst(".cn")?.text()?.trim().orEmpty())
        return info
    }

    /** 封面优先取懒加载地址（`data-src`），退回到 `src`。 */
    private fun thumbnail(row: Element): String? {
        val img = row.selectFirst(".gl2c > .glthumb > div > img") ?: return null
        return img.attr("data-src").takeIf { it.isNotBlank() } ?: img.attr("src")
    }

    /** 页数在隐藏列的第二格里，文本形如 "66 pages"。 */
    private fun pages(row: Element): Int {
        val cell = row.select(".gl4c.glhide > div").getOrNull(1) ?: return 0
        return DIGITS.find(cell.text())?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    /** 分类名（"Doujinshi" 等）→ 位值，与 EhConfig 一致。 */
    private fun categoryBit(name: String): Int = when (name.lowercase()) {
        "misc" -> 0x1
        "doujinshi" -> 0x2
        "manga" -> 0x4
        "artist cg" -> 0x8
        "game cg" -> 0x10
        "image set" -> 0x20
        "cosplay" -> 0x40
        "asian porn" -> 0x80
        "non-h" -> 0x100
        "western" -> 0x200
        else -> 0
    }
}
