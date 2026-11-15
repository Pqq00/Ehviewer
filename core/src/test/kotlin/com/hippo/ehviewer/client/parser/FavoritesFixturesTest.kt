// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.parser

import com.hippo.ehviewer.client.EhConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 收藏页解析 fixture 回归（A1）。
 *
 * 覆盖：`.ido` 里 11 个 `.fp` 槽位的数量/名称、列表条目、分页页数。
 */
class FavoritesFixturesTest {

    private fun fixture(name: String): String {
        val f = File("../fixtures/$name")
        assertTrue("fixture missing: ${f.absolutePath}", f.exists())
        return f.readText()
    }

    private fun parse() = FavoritesParser.parse(fixture("favorites_gltc.html"))

    @Test
    fun favoriteSlotsExposeCountsAndNames() {
        val r = parse()
        assertEquals(10, r.catArray.size)
        assertEquals(10, r.countArray.size)
        assertEquals(3, r.countArray[0])
        assertEquals(1, r.countArray[1])
        assertEquals(0, r.countArray[2])
        assertEquals("Favorites 0", r.catArray[0])
        assertEquals("Favorites 1", r.catArray[1])
        assertEquals("Favorites 9", r.catArray[9])
    }

    @Test
    fun galleryItemsAreParsedFromCompactTable() {
        val r = parse()
        assertEquals(3, r.galleryInfoList.size)
        assertEquals("Favourited Doujinshi One", r.galleryInfoList[0].title)
        assertEquals(3001L, r.galleryInfoList[0].gid)
        assertEquals("Favourited Artbook Two", r.galleryInfoList[1].title)
        assertEquals("Favourited Gallery Three", r.galleryInfoList[2].title)
        assertEquals(57, r.galleryInfoList[2].pages)
    }

    @Test
    fun galleryCategoriesAreMapped() {
        val r = parse()
        assertEquals(EhConfig.DOUJINSHI, r.galleryInfoList[0].category)
        assertEquals(EhConfig.ARTIST_CG, r.galleryInfoList[1].category)
        assertEquals(EhConfig.WESTERN, r.galleryInfoList[2].category)
    }

    @Test
    fun pagingComesFromPttTable() {
        val r = parse()
        assertEquals(2, r.pages)
        assertNotNull(r.galleryInfoList)
        assertTrue("favorites list must not be empty", r.galleryInfoList.isNotEmpty())
    }
}
