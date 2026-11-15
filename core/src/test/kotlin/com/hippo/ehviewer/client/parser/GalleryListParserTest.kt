// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.parser

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Proves that EhViewer's own GalleryListParser runs unmodified on a plain JVM against a real
 * e-hentai front page captured with:
 *
 *   curl -4 -x http://127.0.0.1:7897 --compressed -A "<browser UA>" https://e-hentai.org/
 */
class GalleryListParserTest {

    @Test
    fun parseCapturedHomePage() {
        val fixture = File("../fixtures/home.html")
        assertTrue("fixture missing: ${fixture.absolutePath}", fixture.exists())

        val body = fixture.readText()
        val result = GalleryListParser.parse(body, 0)

        println("=== parsed ${result.galleryInfoList.size} galleries ===")
        result.galleryInfoList.take(10).forEach { gi ->
            println("gid=${gi.gid} cat=${gi.category} thumb=${gi.thumb?.take(60)} title=${gi.title}")
        }

        assertTrue("expected at least one gallery", result.galleryInfoList.isNotEmpty())

        // 首页是游标分页：解析结果必须带出 ?next=<gid>
        println("nextHref=${result.nextHref}")
        assertTrue(
            "front page must expose a next cursor, got ${result.nextHref}",
            result.nextHref?.contains("next=") == true,
        )
    }
}
