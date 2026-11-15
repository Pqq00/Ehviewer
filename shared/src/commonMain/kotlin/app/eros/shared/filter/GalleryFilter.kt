// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.filter

import app.eros.shared.data.GalleryUi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

/** 过滤类型。 */
enum class FilterMode(val label: String) {
    TITLE("标题（包含）"),
    UPLOADER("上传者（全等）"),
    TAG("标签（namespace:name）"),
    NAMESPACE("标签分类（如 female）"),
}

/** 一条过滤规则。命中的画廊会被隐藏。 */
data class FilterRule(
    val mode: FilterMode,
    val text: String,
    val enabled: Boolean = true,
)

/**
 * 画廊过滤器。
 *
 * 匹配语义：
 *  - 标题：忽略大小写「包含」即命中
 *  - 上传者：忽略大小写「全等」即命中
 *  - 标签：`namespace:name` 精确匹配；只写 `name` 则任何 namespace 都算
 *  - 分类：只比较 namespace 部分
 */
object GalleryFilter {

    fun parse(json: String): List<FilterRule> = runCatching {
        Json.parseToJsonElement(json).jsonArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val mode = (obj["mode"] as? JsonPrimitive)?.intOrNull
                ?.let { FilterMode.entries.getOrNull(it) } ?: return@mapNotNull null
            val text = (obj["text"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val enabled = (obj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true
            FilterRule(mode, text, enabled)
        }
    }.getOrDefault(emptyList())

    fun toJson(rules: List<FilterRule>): String = buildJsonArray {
        rules.forEach { rule ->
            add(
                buildJsonObject {
                    put("mode", rule.mode.ordinal)
                    put("text", rule.text)
                    put("enabled", rule.enabled)
                },
            )
        }
    }.toString()

    /** 解析多行文本为规则（每行一条）。 */
    fun fromLines(mode: FilterMode, lines: String, enabled: Boolean = true): List<FilterRule> =
        lines.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { FilterRule(mode, it, enabled) }

    /** 按规则过滤；返回被隐藏的条目。 */
    fun hidden(items: List<GalleryUi>, rules: List<FilterRule>): List<GalleryUi> {
        val active = rules.filter { it.enabled && it.text.isNotBlank() }
        if (active.isEmpty()) return emptyList()
        return items.filter { item -> matches(item, active) }
    }

    fun apply(items: List<GalleryUi>, rules: List<FilterRule>): List<GalleryUi> {
        val active = rules.filter { it.enabled && it.text.isNotBlank() }
        if (active.isEmpty()) return items
        return items.filterNot { item -> matches(item, active) }
    }

    private fun matches(item: GalleryUi, rules: List<FilterRule>): Boolean =
        rules.any { rule -> matchesRule(item, rule) }

    private fun matchesRule(item: GalleryUi, rule: FilterRule): Boolean {
        val text = rule.text
        return when (rule.mode) {
            FilterMode.TITLE ->
                item.title.contains(text, ignoreCase = true) ||
                (item.titleJpn?.contains(text, ignoreCase = true) == true)

            FilterMode.UPLOADER ->
                item.uploader != null && item.uploader.equals(text, ignoreCase = true)

            FilterMode.TAG -> item.tags.any { matchTag(it, text) }

            FilterMode.NAMESPACE -> item.tags.any { tag ->
                val index = tag.indexOf(':')
                index >= 0 && tag.substring(0, index).equals(text, ignoreCase = true)
            }
        }
    }

    /** `namespace:name` 精确匹配；规则只给 name 时忽略 namespace。 */
    private fun matchTag(tag: String, filter: String): Boolean {
        val tagIndex = tag.indexOf(':')
        val filterIndex = filter.indexOf(':')
        val tagNamespace = if (tagIndex >= 0) tag.substring(0, tagIndex) else null
        val tagName = if (tagIndex >= 0) tag.substring(tagIndex + 1) else tag
        val filterNamespace = if (filterIndex >= 0) filter.substring(0, filterIndex) else null
        val filterName = if (filterIndex >= 0) filter.substring(filterIndex + 1) else filter

        if (tagNamespace != null && filterNamespace != null &&
            !tagNamespace.equals(filterNamespace, ignoreCase = true)
        ) {
            return false
        }
        return tagName.equals(filterName, ignoreCase = true)
    }
}
