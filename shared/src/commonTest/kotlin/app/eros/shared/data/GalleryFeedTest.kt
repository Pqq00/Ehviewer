// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

import app.eros.shared.filter.EffectiveFilters
import app.eros.shared.filter.FilterMode
import app.eros.shared.filter.FilterRule
import app.eros.shared.settings.AppSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [GalleryFeed] 的纯函数部分：合并语义与「合并后再过滤」的保证。
 *
 * 这里只测合并/回填顺序，不测网络富化（[GalleryMetaEnricher] 依赖 gdata）。
 */
class GalleryFeedTest {

    private val originalRules = AppSettings.filterRules

    @BeforeTest
    fun setUp() {
        AppSettings.filterRules = emptyList()
    }

    @AfterTest
    fun tearDown() {
        AppSettings.filterRules = originalRules
    }

    private fun ui(gid: Long, title: String = "t$gid", tags: List<String> = emptyList()) = GalleryUi(
        gid = gid,
        token = "tok",
        title = title,
        thumb = null,
        category = 0,
        tags = tags,
    )

    @Test
    fun mergeWithoutPreviousReturnsFresh() {
        val fresh = listOf(ui(1), ui(2))
        assertEquals(fresh, GalleryFeed.merge(emptyList(), fresh))
    }

    @Test
    fun mergeKeepsPreviousOrderAndAppendsNewItems() {
        val previous = listOf(ui(1), ui(2), ui(3))
        val fresh = listOf(ui(2, title = "new2"), ui(4))
        val merged = GalleryFeed.merge(previous, fresh)
        // 旧条目的位置不变（滚动位置不会因为刷新而跳），gid=2 用新数据覆盖，gid=4 追加在尾部
        assertEquals(listOf(1L, 2L, 3L, 4L), merged.map { it.gid })
        assertEquals("new2", merged.first { it.gid == 2L }.title)
        // 旧条目原样保留
        assertEquals("t3", merged.first { it.gid == 3L }.title)
    }

    @Test
    fun mergeDropsDuplicatesFromFresh() {
        val previous = listOf(ui(1))
        val fresh = listOf(ui(1), ui(1), ui(2))
        val merged = GalleryFeed.merge(previous, fresh)
        assertEquals(listOf(1L, 2L), merged.map { it.gid })
    }

    @Test
    fun mergeReappliesFilterToOldItems() {
        // 下拉刷新时旧条目已经带标签，规则改动后应当立刻生效
        val previous = listOf(ui(1, tags = listOf("male:yaoi")), ui(2))
        AppSettings.filterRules = listOf(FilterRule(mode = FilterMode.TAG, text = "male:yaoi"))
        val merged = GalleryFeed.merge(previous, listOf(ui(3)))
        assertEquals(listOf(2L, 3L), merged.map { it.gid })
    }

    @Test
    fun applyRetryReplacesByGidAndFilters() {
        val current = listOf(ui(1), ui(2), ui(3))
        AppSettings.filterRules = listOf(FilterRule(mode = FilterMode.TAG, text = "other:ai generated"))
        val updated = listOf(ui(2, tags = listOf("other:ai generated")))
        val merged = GalleryFeed.applyRetry(current, updated)
        // gid=2 补课拿到命中屏蔽的标签 → 从列表里消失
        assertEquals(listOf(1L, 3L), merged.map { it.gid })
    }

    @Test
    fun applyRetryWithEmptyUpdateReturnsCurrentUnchanged() {
        val current = listOf(ui(1), ui(2))
        assertEquals(current, GalleryFeed.applyRetry(current, emptyList()))
    }

    @Test
    fun filterEnrichedIsIdentityWithoutRules() {
        val items = listOf(ui(1, tags = listOf("male:yaoi")))
        assertEquals(items, EffectiveFilters.filterEnriched(items))
    }
}
