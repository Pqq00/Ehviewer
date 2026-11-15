// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.parser

import com.hippo.ehviewer.client.EhConfig
import com.hippo.ehviewer.client.exception.GalleryUnavailableException
import com.hippo.ehviewer.client.exception.OffensiveException
import com.hippo.ehviewer.client.exception.PiningException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 详情页解析 fixture 回归（A1）。
 *
 * 与 ListFixturesTest 同一套约定：fixture 放在工程根 `fixtures/`，core 测试工作目录为 `core/`。
 * 目的：锁住 GalleryDetailParser 对完整页/最小页/异常页（expunged / offensive / pining）的行为，
 * 让以后改解析器或换 HTML 结构时能立刻发现回归。
 */
class DetailFixturesTest {

    private fun fixture(name: String): String {
        val f = File("../fixtures/$name")
        assertTrue("fixture missing: ${f.absolutePath}", f.exists())
        return f.readText()
    }

    private fun parse(name: String) = GalleryDetailParser.parse(fixture(name))

    private fun failure(name: String): Throwable? =
        runCatching { parse(name) }.exceptionOrNull()

    @Test
    fun fullDetailParsesIdentityAndMeta() {
        val d = parse("detail_full.html")
        assertEquals(2001L, d.gid)
        assertEquals("abcd1234ef", d.token)
        assertEquals("Fixture Full Detail", d.title)
        assertEquals("\u30c6\u30b9\u30c8 Gallery", d.titleJpn)
        assertEquals(EhConfig.DOUJINSHI, d.category)
        assertEquals("Fixture Uploader", d.uploader)
        assertEquals("2024-05-01 12:34", d.posted)
        assertEquals("Japanese", d.language)
        assertEquals("123.4 MB", d.size)
        assertEquals(42, d.pages)
        assertEquals(1300, d.favoriteCount)
    }

    @Test
    fun fullDetailParsesRatingAndFavoriteState() {
        val d = parse("detail_full.html")
        assertEquals(4.62f, d.rating, 0.001f)
        assertEquals(1234, d.ratingCount)
        assertTrue("gdf text is not 'Add to Favorites'", d.isFavorited)
        assertEquals("Favorites 0", d.favoriteName)
    }

    @Test
    fun fullDetailParsesThumb() {
        val d = parse("detail_full.html")
        assertNotNull(d.thumb)
        assertTrue("thumb should point at the fixture cover, got ${d.thumb}", d.thumb.contains("fixture-cover"))
    }

    @Test
    fun fullDetailParsesTagGroups() {
        val d = parse("detail_full.html")
        assertEquals(3, d.tags.size)
        assertEquals("female", d.tags[0].groupName)
        assertEquals(2, d.tags[0].size())
        assertEquals("sole female", d.tags[0].getTagAt(0))
        assertEquals("glasses", d.tags[0].getTagAt(1))
        assertEquals("male", d.tags[1].groupName)
        assertEquals("language", d.tags[2].groupName)
    }

    @Test
    fun fullDetailParsesComments() {
        val d = parse("detail_full.html")
        assertEquals(2, d.comments.comments.size)
        assertEquals("alice", d.comments.comments[0].user)
        assertEquals(5, d.comments.comments[0].score)
        assertEquals("bob", d.comments.comments[1].user)
        assertTrue(d.comments.comments[1].comment.contains("Second comment"))
        assertTrue("'click to show all' should set hasMore", d.comments.hasMore)
    }

    @Test
    fun fullDetailParsesPreviewPagesAndSet() {
        val d = parse("detail_full.html")
        assertEquals(2, d.previewPages)
        assertEquals(2, d.previewSet.size())
        assertEquals(0, d.previewSet.getPosition(0))
        assertEquals(1, d.previewSet.getPosition(1))
        assertTrue(d.previewSet.getPageUrlAt(0).contains("/s/aaa111/"))
    }

    @Test
    fun fullDetailExposesSpiderInfoForReader() {
        val d = parse("detail_full.html")
        assertEquals(42, d.SpiderInfoPages)
        assertEquals(2, d.SpiderInfoPreviewPages)
        assertEquals(2, d.SpiderInfoPreviewSet.size())
    }

    fun minimalDetailUsesEmptyFallbacks() {
        val d = parse("detail_minimal.html")
        assertEquals(2002L, d.gid)
        assertEquals("", d.titleJpn)
        assertEquals("", d.uploader)
        assertEquals(0, d.tags.size)
        assertEquals(0, d.comments.comments.size)
        assertEquals(12, d.pages)
        assertEquals(1, d.previewPages)
        assertTrue("missing gdf must mean not favorited", !d.isFavorited)
    }

    @Test
    fun unratedDetailIsMinusOne() {
        val d = parse("detail_minimal.html")
        assertEquals(-1.0f, d.rating, 0.001f)
        assertEquals(0, d.ratingCount)
        assertEquals(0, d.favoriteCount)
        assertNull(d.favoriteName)
    }

    @Test
    fun expungedDetailThrowsGalleryUnavailable() {
        val e = failure("detail_expunged.html")
        assertTrue("expected GalleryUnavailableException, got $e", e is GalleryUnavailableException)
    }

    @Test
    fun offensiveDetailThrowsOffensive() {
        val e = failure("detail_offensive.html")
        assertTrue("expected OffensiveException, got $e", e is OffensiveException)
    }

    @Test
    fun piningDetailThrowsPining() {
        val e = failure("detail_pining.html")
        assertTrue("expected PiningException, got $e", e is PiningException)
    }

    @Test
    fun detailWithoutGalleryScriptThrowsParseException() {
        val body = fixture("detail_minimal.html")
            .replace("var gid = 2002;", "")
        val e = runCatching { GalleryDetailParser.parse(body) }.exceptionOrNull()
        assertTrue("expected ParseException, got $e", e is com.hippo.ehviewer.client.exception.ParseException)
    }

    @Test
    fun detailWithoutPreviewSetThrowsParseException() {
        val body = fixture("detail_minimal.html")
            .substringBefore("<div id=\"gdt\">")
        val e = runCatching { GalleryDetailParser.parse(body) }.exceptionOrNull()
        assertNotNull("expected failure when preview block is missing", e)
    }
}
