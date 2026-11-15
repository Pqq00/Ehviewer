// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data.cache

import app.eros.shared.currentTimeMillis
import app.eros.shared.data.GalleryDetailUi

/**
 * 详情内存缓存：列表点进详情、返回再进、长按预览共用。
 * 10 分钟内直接命中，避免重复请求详情 HTML。
 */
object DetailMemoryCache {
    private const val MAX_ENTRIES = 16
    private const val TTL_MS = 10 * 60 * 1000L

    private data class Entry(val detail: GalleryDetailUi, val at: Long)

    private val map = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    private fun key(gid: Long, token: String) = "$gid:$token"

    @Synchronized
    fun put(detail: GalleryDetailUi, token: String) {
        map[key(detail.gid, token)] = Entry(detail, currentTimeMillis())
    }

    @Synchronized
    fun get(gid: Long, token: String): GalleryDetailUi? = map[key(gid, token)]?.detail

    @Synchronized
    fun isFresh(gid: Long, token: String, now: Long = currentTimeMillis()): Boolean {
        val e = map[key(gid, token)] ?: return false
        return now - e.at <= TTL_MS
    }

    @Synchronized
    fun clear() = map.clear()
}
