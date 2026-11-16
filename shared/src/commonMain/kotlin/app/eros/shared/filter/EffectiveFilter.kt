// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.filter

import com.hippo.ehviewer.util.AppLog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.eros.shared.data.EhApi
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.WatchedTagUi
import app.eros.shared.data.createEhApi
import app.eros.shared.settings.AppSettings

/**
 * 账号里「隐藏」的关注标签。
 *
 * 站点把 watched/hidden 标签放在 /watched 页面，只有登录后才拿得到；
 * 拉下来后转成普通的过滤规则（TAG 模式），和本地规则一起用。
 */
object HiddenTagStore {

    private var state by mutableStateOf<List<FilterRule>>(emptyList())
    private var loaded = false

    val rules: List<FilterRule>
        get() {
            if (!loaded) {
                loaded = true
                state = GalleryFilter.parse(AppSettings.hiddenTagRulesJson)
            }
            return state
        }

    val count: Int get() = rules.size

    /** 从接口刷新；返回隐藏标签数量。未登录/失败时保持旧快照。 */
    suspend fun refresh(api: EhApi = createEhApi()): Int = try {
        val tags = api.watchedTags()
        update(tags)
        AppLog.i("HiddenTags", "fetched=${tags.size} hidden=${rules.size}")
        rules.size
    } catch (t: kotlinx.coroutines.CancellationException) {
        throw t
    } catch (t: Throwable) {
        AppLog.w("HiddenTags", "refresh failed: ${t.message}")
        count
    }

    /** 关注页已经拿到列表时直接复用，避免重复请求。 */
    fun update(tags: List<WatchedTagUi>) {
        val hidden = tags.filter { it.hidden }
            .map { it.tag.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val list = hidden.map { FilterRule(FilterMode.TAG, it) }
        state = list
        loaded = true
        AppSettings.hiddenTagRulesJson = GalleryFilter.toJson(list)
        AppLog.i("HiddenTags", "synced=${list.size}")
    }

    fun clear() {
        state = emptyList()
        loaded = true
        AppSettings.hiddenTagRulesJson = "[]"
    }
}

/** 本地规则 + 账号隐藏标签（开关控制）合并后的有效规则。 */
object EffectiveFilters {

    fun rules(): List<FilterRule> {
        val local = AppSettings.filterRules
        if (!AppSettings.useHiddenTags) return local
        val merged = LinkedHashMap<String, FilterRule>()
        local.forEach { merged[it.key()] = it }
        HiddenTagStore.rules.forEach { merged.putIfAbsent(it.key(), it) }
        return merged.values.toList()
    }

    /** 过滤一页；返回过滤后的列表。 */
    fun filter(items: List<GalleryUi>): List<GalleryUi> = GalleryFilter.apply(items, rules())

    /**
     * 富化之后再过滤一遍。
     *
     * 列表页 HTML / 排行榜紧凑页只带极少数标签（甚至一个都没有），真正的标签要等
     * `GalleryMetaEnricher.enrichPage` 拿 gdata 才补齐。所以「标签屏蔽」必须在富化
     * 之后再过一次，否则 `FilterMode.TAG` / `NAMESPACE` 规则永远命中不了。
     */
    fun filterEnriched(items: List<GalleryUi>): List<GalleryUi> = filter(items)
}

private fun FilterRule.key(): String = "$mode|${text.trim().lowercase()}|$enabled"
