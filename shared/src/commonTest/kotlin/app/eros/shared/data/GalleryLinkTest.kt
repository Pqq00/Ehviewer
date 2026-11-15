// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GalleryLinkTest {

    @Test
    fun parsesStandardGalleryUrl() {
        val ref = GalleryLink.parse("https://e-hentai.org/g/4133567/0cde39d529/")
        assertNotNull(ref)
        assertEquals(4133567L, ref.gid)
        assertEquals("0cde39d529", ref.token)
    }

    @Test
    fun parsesVariants() {
        // 无协议
        assertEquals(
            GalleryRef(4133567L, "0cde39d529"),
            GalleryLink.parse("e-hentai.org/g/4133567/0cde39d529"),
        )
        // exhentai + 无尾斜杠
        assertEquals(
            GalleryRef(4133567L, "0cde39d529"),
            GalleryLink.parse("https://exhentai.org/g/4133567/0cde39d529"),
        )
        // 带查询串与锚点
        assertEquals(
            GalleryRef(4133567L, "0cde39d529"),
            GalleryLink.parse("https://e-hentai.org/g/4133567/0cde39d529/?nw=session#comments"),
        )
        // 子域名（lofi）
        assertEquals(
            GalleryRef(4133567L, "0cde39d529"),
            GalleryLink.parse("https://lofi.e-hentai.org/g/4133567/0cde39d529/"),
        )
        // 中文标点尾随
        assertEquals(
            GalleryRef(4133567L, "0cde39d529"),
            GalleryLink.parse("https://e-hentai.org/g/4133567/0cde39d529/）"),
        )
    }

    @Test
    fun rejectsNonGallery() {
        assertNull(GalleryLink.parse("https://www.pixiv.net/users/110204350"))
        assertNull(GalleryLink.parse("https://e-hentai.org/"))
        // 没有 token 的 /g/<gid>/ 不认，交给浏览器
        assertNull(GalleryLink.parse("https://e-hentai.org/g/4133567/"))
        assertNull(GalleryLink.parse("hello world"))
        assertNull(GalleryLink.parse(""))
    }

    @Test
    fun extractsLinksFromCommentText() {
        val text = "站内：https://e-hentai.org/g/4133567/0cde39d529/ 站外：https://www.pixiv.net/users/110204350"
        val spans = GalleryLink.findAll(text)
        assertEquals(2, spans.size)
        assertEquals("https://e-hentai.org/g/4133567/0cde39d529/", spans[0].url)
        assertEquals("https://www.pixiv.net/users/110204350", spans[1].url)
        // 位置必须能在原文里取出同一段文字
        assertEquals(spans[0].url, text.substring(spans[0].start, spans[0].end))
        assertEquals(spans[1].url, text.substring(spans[1].start, spans[1].end))
    }

    @Test
    fun firstReturnsGalleryFromMixedText() {
        val text = "见 https://www.pixiv.net/users/110204350 和 https://e-hentai.org/g/4133567/0cde39d529/"
        assertEquals(GalleryRef(4133567L, "0cde39d529"), GalleryLink.first(text))
        assertNull(GalleryLink.first("没有链接"))
    }

    @Test
    fun externalUrlAddsScheme() {
        assertEquals("https://e-hentai.org/g/1/abc/", GalleryLink.externalUrl("e-hentai.org/g/1/abc/"))
        assertEquals("http://a.b/c", GalleryLink.externalUrl("http://a.b/c"))
    }
}
