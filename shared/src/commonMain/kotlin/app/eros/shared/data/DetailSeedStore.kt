// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

/**
 * 列表页已知信息的「种子」表。
 *
 * 点卡片进详情页时，列表其实已经有标题/封面/分类/页数/标签/上传者；
 * 把这份数据按 `gid:token` 存起来，详情页首屏可以立刻渲染头部，
 * 不用等详情 HTML 返回。
 *
 * 访问序 LRU，最多 [MAX] 条；详情页只读取，不写入。
 */
object DetailSeedStore {

    private const val MAX = 32

    private val lock = Any()

    private val cache = object : LinkedHashMap<String, GalleryUi>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GalleryUi>): Boolean =
            size > MAX
    }

    fun put(ui: GalleryUi) {
        synchronized(lock) { cache["${ui.gid}:${ui.token}"] = ui }
    }

    fun get(gid: Long, token: String): GalleryUi? = synchronized(lock) { cache["$gid:$token"] }

    fun clear() = synchronized(lock) { cache.clear() }
}
