// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.parser

import com.hippo.ehviewer.client.data.GalleryInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * gdata 补全的账目：站点对已删除的画廊不报错，只默默少返回条目，
 * 统计要能把这些「没补上」的情况算出来。
 */
class GalleryApiParserStatsTest {

    private fun info(gid: Long) = GalleryInfo().apply {
        this.gid = gid
        this.token = "tok$gid"
    }

    @Test
    fun `全部补齐时没有缺口`() {
        val list = listOf(info(1), info(2))
        val body = """
            {"gmetadata":[
              {"gid":1,"title":"One","category":"Doujinshi","filecount":"12"},
              {"gid":2,"title":"Two","category":"Manga","filecount":"3"}
            ]}
        """.trimIndent()

        val stats = GalleryApiParser.parse(body, list)

        assertEquals(2, stats.requested)
        assertEquals(2, stats.returned)
        assertEquals(2, stats.matched)
        assertEquals(2, stats.updated)
        assertEquals(0, stats.skipped)
        assertEquals(0, stats.missing)
        assertFalse(stats.hasGaps())
        assertEquals("One", list[0].title)
        assertEquals(12, list[0].pages)
    }

    @Test
    fun `已删除条目计入跳过与缺失`() {
        val list = listOf(info(1), info(2), info(3))
        val body = """
            {"gmetadata":[
              {"gid":1,"title":"One"},
              {"gid":2,"error":"此画廊已被删除"}
            ]}
        """.trimIndent()

        val stats = GalleryApiParser.parse(body, list)

        assertEquals(3, stats.requested)
        assertEquals(2, stats.returned)
        assertEquals(1, stats.updated)
        assertEquals(1, stats.skipped)
        // 3 请求 - 2 返回 = 1 条站点没给
        assertEquals(1, stats.missing)
        assertTrue(stats.hasGaps())
        assertTrue(stats.summary().contains("缺失 1"))
    }

    @Test
    fun `没有 gmetadata 时整批算缺失`() {
        val list = listOf(info(1), info(2))

        val stats = GalleryApiParser.parse("""{"error":"bad request"}""", list)

        assertEquals(2, stats.requested)
        assertEquals(0, stats.returned)
        assertEquals(2, stats.missing)
        assertTrue(stats.hasGaps())
    }
}
