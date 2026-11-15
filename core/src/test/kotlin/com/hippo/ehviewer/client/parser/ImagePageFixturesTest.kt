// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.parser

import com.hippo.ehviewer.client.exception.ParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 图片页异常 fixture 回归（A1）。
 *
 * 509 / 带宽超限 / 缺 showkey 都必须抛 ParseException，不能返回半个 Result 让上层下载崩掉。
 */
class ImagePageFixturesTest {

    private fun fixture(name: String): String {
        val f = File("../fixtures/$name")
        assertTrue("fixture missing: ${f.absolutePath}", f.exists())
        return f.readText()
    }

    @Test
    fun bandwidthLimitPageThrowsParseException() {
        val e = runCatching { GalleryPageParser.parse(fixture("image_page_509.html")) }.exceptionOrNull()
        assertTrue("expected ParseException, got $e", e is ParseException)
    }

    @Test
    fun pageWithoutShowkeyThrowsParseException() {
        val body = "<html><body><img src=\"https://ehgt.org/x.jpg\" style=\"height:10px\" /></body></html>"
        val e = runCatching { GalleryPageParser.parse(body) }.exceptionOrNull()
        assertTrue("expected ParseException, got $e", e is ParseException)
    }

    @Test
    fun pageWithoutImageThrowsParseException() {
        val body = "<html><body><script>var showkey=\"abc123\";</script></body></html>"
        val e = runCatching { GalleryPageParser.parse(body) }.exceptionOrNull()
        assertTrue("expected ParseException, got $e", e is ParseException)
    }

    @Test
    fun validPageStillParses() {
        val r = GalleryPageParser.parse(fixture("image_page.html"))
        assertEquals("abcdef1234", r.showKey)
        assertTrue(r.imageUrl.contains("ehgt.org"))
    }
}
