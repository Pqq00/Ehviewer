// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data.cache

import app.eros.shared.data.GalleryDetailUi
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.TagGroupUi
import app.eros.shared.currentTimeMillis
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GalleryCacheTest {

    private fun gallery(gid: Long, title: String) = GalleryUi(
        gid = gid,
        token = "t$gid",
        title = title,
        thumb = "https://example.com/$gid.jpg",
        category = 1,
        tags = listOf("language:chinese", "female:bikini"),
        uploader = "uploader$gid",
        pages = 42,
        rating = 4.5f,
        posted = "2024-05-01 12:34",
    )

    @Test
    fun mergedPageRoundTrips() = runBlocking {
        val key = "test:home:${currentTimeMillis()}"
        GalleryCache.saveMerged(key, listOf(gallery(1, "one"), gallery(2, "two")), "next-cursor")

        val page = GalleryCache.page(key)
        assertNotNull(page)
        assertEquals(listOf(1L, 2L), page.items.map { it.gid })
        assertEquals("next-cursor", page.nextHref)
        val first = page.items.first()
        assertEquals("one", first.title)
        assertEquals(42, first.pages)
        assertEquals(4.5f, first.rating)
        assertEquals("2024-05-01 12:34", first.posted)
        assertEquals(listOf("language:chinese", "female:bikini"), first.tags)
    }

    @Test
    fun emptyPageMisses() = runBlocking {
        assertNull(GalleryCache.page("test:missing:${currentTimeMillis()}"))
    }

    @Test
    fun detailRoundTrips() = runBlocking {
        val gid = currentTimeMillis()
        val detail = GalleryDetailUi(
            gid = gid,
            title = "detail",
            thumb = "https://example.com/d.jpg",
            category = 2,
            uploader = "u",
            posted = "2024-05-01 12:34",
            pages = 12,
            rating = 4.2f,
            favoriteCount = 9,
            isFavorited = true,
            language = "Chinese",
            size = "12 MiB",
            tags = listOf(TagGroupUi("female", listOf("bikini", "sole female"))),
            ratingCount = 33,
        )
        GalleryCache.saveDetail(detail, "tok")

        val loaded = GalleryCache.detail(gid, "tok")
        assertNotNull(loaded)
        assertEquals("detail", loaded.title)
        assertEquals(12, loaded.pages)
        assertEquals(4.2f, loaded.rating)
        assertEquals(9, loaded.favoriteCount)
        assertTrue(loaded.isFavorited)
        assertEquals("Chinese", loaded.language)
        assertEquals(1, loaded.tags.size)
        assertEquals(listOf("bikini", "sole female"), loaded.tags.first().tags)
        assertEquals(33, loaded.ratingCount)
    }

    @Test
    fun detailTokenMismatchMisses() = runBlocking {
        val gid = currentTimeMillis()
        val detail = GalleryDetailUi(
            gid = gid,
            title = "detail-token",
            thumb = null,
            category = 0,
            uploader = null,
            posted = null,
            pages = 1,
            rating = -1f,
            favoriteCount = 0,
            isFavorited = false,
            language = null,
            size = null,
            tags = emptyList(),
        )
        GalleryCache.saveDetail(detail, "right")
        assertNull(GalleryCache.detail(gid, "wrong"))
    }

    @Test
    fun clearListPagesKeepsDetails() = runBlocking {
        val stamp = currentTimeMillis()
        val key = "test:clear:$stamp"
        GalleryCache.saveMerged(key, listOf(gallery(101, "keep me")), null)
        assertNotNull(GalleryCache.page(key))

        val gid = stamp + 3
        GalleryCache.saveDetail(
            GalleryDetailUi(
                gid = gid,
                title = "detail stays",
                thumb = null,
                category = 0,
                uploader = null,
                posted = null,
                pages = 1,
                rating = -1f,
                favoriteCount = 0,
                isFavorited = false,
                language = null,
                size = null,
                tags = emptyList(),
            ),
            "tok",
        )

        GalleryCache.clearListPages()

        assertNull(GalleryCache.page(key))
        assertNotNull(GalleryCache.detail(gid, "tok"))
        Unit
    }
}
