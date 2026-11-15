// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.settings

import kotlinx.serialization.Serializable

/**
 * 站点分类位（与 e-hentai 的 f_cats 一致）。
 *
 * 这里的位是「站点分类」，不是筛选掩码，改动会和站点语义脱节，所以单独放一份。
 */
object HomeCats {
    const val MISC = 0x1
    const val DOUJINSHI = 0x2
    const val MANGA = 0x4
    const val ARTIST_CG = 0x8
    const val GAME_CG = 0x10
    const val IMAGE_SET = 0x20
    const val COSPLAY = 0x40
    const val ASIAN_PORN = 0x80
    const val NON_H = 0x100
    const val WESTERN = 0x200

    const val ALL = MISC or DOUJINSHI or MANGA or ARTIST_CG or GAME_CG or
        IMAGE_SET or COSPLAY or ASIAN_PORN or NON_H or WESTERN
}

/**
 * 首页分类行里的一个条目。
 *
 * 两种形态：
 * - 内置分类：`kind = cat`，按 [mask] 走首页列表 `frontPage(cats)`；
 * - 自定义分组：`kind = group`，把 [includeTags]/[excludeTags] 拼成关键词走搜索，
 *   可选 [cats] 把结果限定在若干分类里。
 *
 * 「热门」和「全部」不在这里：热门固定在最前（写死），全部是默认条目的第一项，
 * 但两者都可以不参与编辑（热门）或被改名/移动（全部）。
 */
@Serializable
data class HomeEntry(
    val kind: String = KIND_CAT,
    val mask: Int = HomeCats.ALL,
    val label: String = "",
    val id: String = "",
    val includeTags: List<String> = emptyList(),
    val excludeTags: List<String> = emptyList(),
    val cats: Int? = null,
) {

    val isGroup: Boolean get() = kind == KIND_GROUP

    /** 稳定标识：内置分类用掩码，分组用 id。路由、缓存、选中判断都用它。 */
    val key: String get() = if (isGroup) "g:$id" else "cat:$mask"

    /**
     * 分组展开成的搜索关键词。
     *
     * 包含标签原样平铺（站点用空格分隔多个标签），排除标签加 `-` 前缀；
     * 两条排除、三条包含都是合法写法。
     */
    fun searchQuery(): String {
        if (!isGroup) return ""
        return buildList {
            includeTags.forEach { tag -> tag.trim().takeIf { it.isNotEmpty() }?.let(::add) }
            excludeTags.forEach { tag ->
                val clean = tag.trim().removePrefix("-")
                if (clean.isNotEmpty()) add("-$clean")
            }
        }.joinToString(" ")
    }

    /** 站点 `f_cats` 的语义是「排除哪些分类」，0 表示不排除。 */
    val excludeCats: Int
        get() {
            val allowed = cats ?: return 0
            return HomeCats.ALL and allowed.inv()
        }

    companion object {
        const val KIND_CAT = "cat"
        const val KIND_GROUP = "group"

        fun cat(mask: Int, label: String): HomeEntry =
            HomeEntry(kind = KIND_CAT, mask = mask, label = label)

        fun group(
            id: String,
            label: String,
            includeTags: List<String> = emptyList(),
            excludeTags: List<String> = emptyList(),
            cats: Int? = null,
        ): HomeEntry = HomeEntry(
            kind = KIND_GROUP,
            mask = 0,
            label = label,
            id = id,
            includeTags = includeTags,
            excludeTags = excludeTags,
            cats = cats,
        )
    }
}

/** 自定义分组最多几个（不含内置分类）。 */
const val MAX_CUSTOM_GROUPS = 8

/** 默认 chips：「全部」在前，其余是内置分类。热门固定在最前，不在这里。 */
val DEFAULT_HOME_ENTRIES: List<HomeEntry> = listOf(
    HomeEntry.cat(HomeCats.ALL, "全部"),
    HomeEntry.cat(HomeCats.DOUJINSHI, "同人志"),
    HomeEntry.cat(HomeCats.MANGA, "漫画"),
    HomeEntry.cat(HomeCats.ARTIST_CG or HomeCats.IMAGE_SET, "画集"),
    HomeEntry.cat(HomeCats.COSPLAY, "Cosplay"),
    HomeEntry.cat(HomeCats.NON_H, "Non-H"),
)

/** 编辑页「类别」可勾选的分类（和默认 chips 一致）。 */
val HOME_CATEGORY_CHOICES: List<HomeEntry> = listOf(
    HomeEntry.cat(HomeCats.DOUJINSHI, "同人志"),
    HomeEntry.cat(HomeCats.MANGA, "漫画"),
    HomeEntry.cat(HomeCats.ARTIST_CG or HomeCats.IMAGE_SET, "画集"),
    HomeEntry.cat(HomeCats.COSPLAY, "Cosplay"),
    HomeEntry.cat(HomeCats.NON_H, "Non-H"),
    HomeEntry.cat(HomeCats.MISC, "Misc"),
    HomeEntry.cat(HomeCats.GAME_CG, "Game CG"),
    HomeEntry.cat(HomeCats.ASIAN_PORN, "Asian Porn"),
    HomeEntry.cat(HomeCats.WESTERN, "Western"),
)
