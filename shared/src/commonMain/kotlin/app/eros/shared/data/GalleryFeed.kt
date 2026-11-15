// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

import app.eros.shared.filter.EffectiveFilters

/**
 * 一个列表页「富化 + 过滤」之后的结果。
 *
 * @property items 可以发布的条目（已按当前规则过滤）
 * @property hidden 本页被规则挡掉的条数（含账号隐藏标签）
 * @property failed 富化失败、交给后台补课的 gid
 */
data class EnrichedFeed(
    val items: List<GalleryUi>,
    val hidden: Int,
    val failed: Set<Long>,
)

/**
 * 列表页共用的一条流水线：**先富化、再过滤**。
 *
 * 四个列表页（首页 / 排行榜 / 搜索 / 收藏）原先各自写了同一段
 * 「filter → enrichPage → 按 gid 替换 → filterEnriched → saveMerged」，
 * 顺序一改就可能退回「标签屏蔽对列表无效」的老问题（列表 HTML 本身几乎没有标签，
 * 标签只来自 gdata 富化）。这里把顺序固定下来，各页只负责取页、合并与落盘。
 */
object GalleryFeed {

    /**
     * 富化一个网络页并过滤。
     *
     * @param raw 网络页原始条目（可能一个标签都没有）
     */
    suspend fun enrich(
        api: EhApi,
        raw: List<GalleryUi>,
        timeoutMs: Long = GalleryMetaEnricher.PAGE_TIMEOUT_MS,
    ): EnrichedFeed {
        if (raw.isEmpty()) return EnrichedFeed(emptyList(), 0, emptySet())
        val visible = EffectiveFilters.filter(raw)
        val res = GalleryMetaEnricher.enrichPage(api, visible, timeoutMs)
        val byGid = res.updated.associateBy { it.gid }
        val enriched = if (byGid.isEmpty()) visible else visible.map { byGid[it.gid] ?: it }
        val items = EffectiveFilters.filterEnriched(enriched)
        return EnrichedFeed(items, raw.size - items.size, res.failedGids)
    }

    /**
     * 把网络页并进已有列表：同 gid 用新数据覆盖，旧条目保留（刷新不缩水），
     * 合并后再过滤一次 —— 旧条目身上本来就有标签，规则改动后刷新应当立即生效。
     */
    fun merge(previous: List<GalleryUi>, fresh: List<GalleryUi>): List<GalleryUi> {
        if (previous.isEmpty()) return fresh
        if (fresh.isEmpty()) return EffectiveFilters.filterEnriched(previous)
        val freshByGid = fresh.associateBy { it.gid }
        val base = previous.map { freshByGid[it.gid] ?: it }
        val seen = HashSet<Long>(base.size * 2)
        base.forEach { seen.add(it.gid) }
        return EffectiveFilters.filterEnriched(base + fresh.filter { seen.add(it.gid) })
    }

    /** 后台补课结果并入当前列表：同 gid 覆盖，然后重新过滤（补回的标签也可能命中屏蔽）。 */
    fun applyRetry(current: List<GalleryUi>, updated: List<GalleryUi>): List<GalleryUi> {
        if (updated.isEmpty()) return current
        val byGid = updated.associateBy { it.gid }
        return EffectiveFilters.filterEnriched(current.map { byGid[it.gid] ?: it })
    }
}
