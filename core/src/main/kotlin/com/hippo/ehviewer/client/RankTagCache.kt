// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client

/**
 * 排行榜的补标签缓存。
 *
 * `toplist.php` 的紧凑列表里没有标签，标签要靠 `api.php` 的 gdata 再取一次。
 * 切换「今日 / 本月 / 今年 / 全部」时同一个 gid 会反复出现，所以在会话内留一份，
 * 并按访问顺序淘汰，上限 [MAX_ENTRIES] 条。
 */
object RankTagCache {

    private const val MAX_ENTRIES = 2000

    private val lock = Any()

    /** accessOrder = true：读到就移到队尾，淘汰时从队头删。 */
    private val entries = LinkedHashMap<Long, List<String>>(64, 0.75f, true)

    fun get(gid: Long): List<String>? = synchronized(lock) { entries[gid] }

    fun put(gid: Long, tags: List<String>) {
        if (tags.isEmpty()) {
            return
        }
        synchronized(lock) {
            entries[gid] = tags
            while (entries.size > MAX_ENTRIES) {
                val eldest = entries.keys.iterator()
                if (!eldest.hasNext()) {
                    break
                }
                eldest.next()
                eldest.remove()
            }
        }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }
}
