/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.client

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject

/**
 * 标签翻译数据库（EhTagTranslation）。
 *
 * 数据来源是社区维护的 `database.json`，结构大致为：
 * ```
 * { "data": [ { "namespace": "female", "data": { "bikini": { "name": "比基尼" } } } ] }
 * ```
 * 这里不再做 EhViewer 那套二进制 + 二分查找，直接建 Map：
 * 条目数量在几万级，内存占用可以接受，代码简单得多。
 */
object EhTagDatabase {

    private var map: Map<String, String> = emptyMap()

    val size: Int get() = map.size
    val loaded: Boolean get() = map.isNotEmpty()

    /** 解析并载入；返回条目数。 */
    fun load(json: String): Int {
        val result = HashMap<String, String>(4096)
        runCatching {
            val root = JSON.parseObject(json)
            val data = root?.getJSONArray("data")
            if (data != null) {
                for (i in 0 until data.size) {
                    val nsObj = data.getJSONObject(i) ?: continue
                    val namespace = nsObj.getString("namespace").orEmpty()
                    val tags = nsObj.getJSONObject("data") ?: continue
                    for (tag in tags.keys) {
                        // 不同产物里 tag 的值可能是字符串或 {name: ...} 对象
                        val name = when (val raw = tags[tag]) {
                            is String -> raw
                            is JSONObject -> raw.getString("name")
                            is Map<*, *> -> raw["name"] as? String
                            else -> null
                        }?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                        if (namespace.isNotBlank()) {
                            result["$namespace:$tag"] = name
                        }
                        // 同时存一份不带 namespace 的键，方便只写标签名时也能查到
                        if (!result.containsKey(tag)) result[tag] = name
                    }
                }
            }
        }
        map = result
        return map.size
    }

    /** 查翻译；支持 `namespace:tag` 或只给 `tag`。 */
    fun translate(tag: String): String? =
        map[tag] ?: map[tag.lowercase()] ?: run {
            val index = tag.indexOf(':')
            if (index >= 0) map[tag.substring(index + 1)] else null
        }

    /** 当前条目快照，供外部落盘成预解析索引。 */
    fun snapshot(): Map<String, String> = map

    /** 直接载入已解析好的条目（本地索引），跳过 JSON 解析。 */
    fun loadEntries(entries: Map<String, String>): Int {
        map = entries
        return map.size
    }

    fun clear() {
        map = emptyMap()
    }
}
