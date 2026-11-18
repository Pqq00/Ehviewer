// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Parser fixture 测试（T2）。
 *
 * 目标：不联网、可复现地锁住 E-H 四种列表版式 + 空结果 + 图片页的解析行为。
 * fixture 用最小 HTML 复刻真实页面结构（类名与层级与线上一致），
 * 放在工程根 `fixtures/`，core 模块测试的工作目录是 `core/`，故用 `../fixtures`。
 *
 * 覆盖点：gid / token / 标题 / 分类 / 上传者 / 页数 / 游标 nextHref。
 */
class ListFixturesTest {

    private fun fixture(name: String): String {
        val f = File("../fixtures/$name")
        assertTrue("fixture missing: ${f.absolutePath}", f.exists())
        return f.readText()
    }

    private fun parse(name: String): GalleryListParser.Result =
        GalleryListParser.parse(fixture(name), 0)

    @Test
    fun minimalLayoutParsesTwoGalleries() {
        val result = parse("list_minimal.html")
        val list = result.galleryInfoList
        assertEquals(2, list.size)
        assertEquals(1001L, list[0].gid)
        assertEquals("aaaa1111aa", list[0].token)
        assertEquals("Fixture Alpha", list[0].title)
        assertEquals(42, list[0].pages)
        assertEquals("Uploader One", list[0].uploader)
        assertEquals(1002L, list[1].gid)
        assertEquals("Fixture Beta", list[1].title)
        assertEquals(15, list[1].pages)
    }

    @Test
    fun compactLayoutParsesThumbAndPages() {
        val result = parse("list_compact.html")
        val list = result.galleryInfoList
        assertEquals(2, list.size)
        assertEquals("Fixture Alpha", list[0].title)
        assertEquals(42, list[0].pages)
        assertNotNull("compact thumb should be parsed", list[0].thumb)
        assertEquals("Fixture Beta", list[1].title)
        assertEquals(15, list[1].pages)
    }

    @Test
    fun extendedLayoutParsesUploaderAndPages() {
        val result = parse("list_extended.html")
        val list = result.galleryInfoList
        assertEquals(2, list.size)
        assertEquals("Fixture Gamma", list[0].title)
        assertEquals(88, list[0].pages)
        assertEquals("Uploader Three", list[0].uploader)
        assertEquals("Fixture Delta", list[1].title)
        assertEquals(7, list[1].pages)
    }

    @Test
    fun thumbnailLayoutParsesThumbAndPages() {
        val result = parse("list_thumbnail.html")
        val list = result.galleryInfoList
        assertEquals(2, list.size)
        assertEquals("Fixture Epsilon", list[0].title)
        assertEquals(120, list[0].pages)
        assertNotNull("thumbnail thumb should be parsed", list[0].thumb)
        assertEquals("Fixture Zeta", list[1].title)
        assertEquals(9, list[1].pages)
    }

    @Test
    fun cursorNavigationIsExposed() {
        val result = parse("list_minimal.html")
        assertEquals("?next=2002", result.nextHref)
        assertEquals("?next=9999", result.prevHref)
    }

    @Test
    fun emptySearchReturnsNoItems() {
        val result = parse("search_empty.html")
        assertEquals(0, result.galleryInfoList.size)
        assertEquals(0, result.pages)
    }

    @Test
    fun emptySearchIsOkNotError() {
        val result = GalleryListParser.parseResult(fixture("search_empty.html"), 0)
        assertTrue("空搜索是成功而不是失败：$result", result is ParseResult.Ok)
        assertEquals(0, (result as ParseResult.Ok).value.galleryInfoList.size)
    }

    @Test
    fun unparseablePageIsExplicitError() {
        val body = "<html><body><h1>Just a moment...</h1></body></html>"
        val result = GalleryListParser.parseResult(body, 0)
        assertTrue("验证码/登录墙页面必须显式失败：$result", result is ParseResult.Err)
        val err = result as ParseResult.Err
        assertTrue("失败要带原因", err.message.isNotBlank())
        assertTrue("失败要保留原始页面便于排查", err.body == body)
    }

    @Test
    fun parseThrowsOnUnparseablePage() {
        val failed = runCatching {
            GalleryListParser.parse("<html><body><p>blocked</p></body></html>", 0)
        }
        assertTrue("旧 API 也应在看不懂页面时抛 ParseException", failed.isFailure)
        assertTrue(failed.exceptionOrNull() is com.hippo.ehviewer.client.exception.ParseException)
    }

    @Test
    fun imagePageParsesUrlShowkeyAndFullImage() {
        val result = GalleryPageParser.parse(fixture("image_page.html"))
        assertEquals("https://ehgt.org/h/fixture-page.jpg", result.imageUrl)
        assertEquals("abcdef1234", result.showKey)
        assertEquals("skipkey99", result.skipHathKey)
        assertNotNull(result.originImageUrl)
        assertTrue(result.originImageUrl!!.contains("fullimg"))
    }

    @Test
    fun imagePageWithoutShowkeyFails() {
        val broken = "<html><body><img src=\"https://ehgt.org/x.jpg\" style=\"height:10px\" /></body></html>"
        val failed = runCatching { GalleryPageParser.parse(broken) }.isFailure
        assertTrue("page without showkey must throw ParseException", failed)
    }
    @Test
    fun brokenItemIsSkipped() {
        val html = """
            <html><body>
            <div class="searchnav"><a id="unext" href="?next=1">next</a></div>
            <div class="itg gltm">
              <div class="glitem"><div class="cn">Manga</div><div class="glname"><a href="https://e-hentai.org/g/2001/aaaa0001aa/"><div class="glink">Valid One</div></a></div><div class="glhide"><div><a>up</a></div><div>3 pages</div></div></div>
              <div class="glitem"><div class="cn">Manga</div></div>
              <div class="glitem"><div class="cn">Manga</div><div class="glname"><a href="https://e-hentai.org/g/2002/aaaa0002aa/"><div class="glink">Valid Two</div></a></div><div class="glhide"><div><a>up</a></div><div>4 pages</div></div></div>
            </div>
            </body></html>
        """.trimIndent()
        val result = GalleryListParser.parse(html, 0)
        assertEquals(2, result.galleryInfoList.size)
        assertEquals("Valid One", result.galleryInfoList[0].title)
        assertEquals("Valid Two", result.galleryInfoList[1].title)
    }

    @Test
    fun categoryIsMappedToEhCategory() {
        val result = parse("list_minimal.html")
        assertEquals(com.hippo.ehviewer.client.EhConfig.DOUJINSHI, result.galleryInfoList[0].category)
        assertEquals(com.hippo.ehviewer.client.EhConfig.MANGA, result.galleryInfoList[1].category)
    }
}