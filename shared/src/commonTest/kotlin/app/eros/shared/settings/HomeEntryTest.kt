// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 首页分类行条目（内置分类 / 热门之外的「自定义分组」）的行为约束：
 * - 分组靠包含标签 + 排除标签拼出站点搜索串；
 * - 限定类别要换成站点需要的「排除位」；
 * - key 必须在分类与分组之间不冲突。
 */
class HomeEntryTest {

    @Test
    fun groupQueryJoinsIncludeAndExcludeTags() {
        val group = HomeEntry.group(
            id = "g1",
            label = "我的分组",
            includeTags = listOf("big breasts", "females"),
            excludeTags = listOf("scat", "guro"),
        )
        assertEquals("big breasts females -scat -guro", group.searchQuery())
    }

    @Test
    fun groupQueryOnlyIncludesSelectedSides() {
        val includeOnly = HomeEntry.group(
            id = "g2",
            label = "只要包含",
            includeTags = listOf("yuri"),
        )
        assertEquals("yuri", includeOnly.searchQuery())

        val excludeOnly = HomeEntry.group(
            id = "g3",
            label = "只要排除",
            includeTags = emptyList(),
            excludeTags = listOf("ntr"),
        )
        assertEquals("-ntr", excludeOnly.searchQuery())
    }

    @Test
    fun excludeCatsIsComplementOfSelectedCats() {
        val group = HomeEntry.group(
            id = "g4",
            label = "只看漫画",
            includeTags = listOf("big breasts"),
            cats = HomeCats.MANGA,
        )
        assertEquals(HomeCats.ALL and HomeCats.MANGA.inv(), group.excludeCats)
        // 站点把「不限定」当作不传，这里 0 就表示不传。
        assertEquals(0, group.copy().copyWithoutCats().excludeCats)
    }

    @Test
    fun keyDiffersBetweenCatAndGroup() {
        assertEquals("cat:${HomeCats.MANGA}", HomeEntry.cat(HomeCats.MANGA, "漫画").key)
        assertEquals("g:abc", HomeEntry.group("abc", "分组").key)
        assertNull(HomeEntry.group("x", "y").cats)
    }

    @Test
    fun defaultEntriesKeepOrderAndStartWithAll() {
        assertTrue(DEFAULT_HOME_ENTRIES.first().mask == HomeCats.ALL)
        assertTrue(DEFAULT_HOME_ENTRIES.none { it.isGroup })
        assertEquals("全部", DEFAULT_HOME_ENTRIES.first().label)
    }
}

/** 测试辅助：把 cats 清成 null（首页分类 = 不限类别时站点不传 f_cats）。 */
private fun HomeEntry.copyWithoutCats(): HomeEntry = HomeEntry.group(
    id = id,
    label = label,
    includeTags = includeTags,
    excludeTags = excludeTags,
    cats = null,
)
