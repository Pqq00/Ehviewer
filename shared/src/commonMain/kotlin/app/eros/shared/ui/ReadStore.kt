// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.eros.shared.settings.platformGetString
import app.eros.shared.settings.platformSetString

/**
 * 已读标记与阅读进度：本地记录打开过详情的 gid，以及读到第几页。
 *
 * 已读列表存成逗号分隔的 gid 串（上限 [CAP] 条，超出丢最早的）；
 * 进度存成 `gid:page:pages` 的串（上限 [PROGRESS_CAP] 条），两者都用
 * [mutableStateOf] 暴露，卡片读取时能自动重组。
 */
object ReadStore {

    private const val KEY = "read_gids"
    private const val CAP = 1000

    private const val PROGRESS_KEY = "read_progress"
    private const val PROGRESS_CAP = 500

    private var ids by mutableStateOf<Set<Long>>(emptySet())
    private var loaded = false

    /** gid -> (已读到第几页, 总页数)。 */
    private var progress by mutableStateOf<Map<Long, Pair<Int, Int>>>(emptyMap())
    private var progressLoaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = platformGetString(KEY, "")
        if (raw.isBlank()) return
        ids = raw.split(',').mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun ensureProgressLoaded() {
        if (progressLoaded) return
        progressLoaded = true
        val raw = platformGetString(PROGRESS_KEY, "")
        if (raw.isBlank()) return
        progress = raw.split(',').mapNotNull { entry ->
            val parts = entry.split(':')
            val gid = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val page = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val pages = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            if (pages <= 0) return@mapNotNull null
            gid to (page to pages)
        }.toMap()
    }

    fun markRead(gid: Long) {
        ensureLoaded()
        if (gid in ids) return
        val next = (ids + gid).toList().takeLast(CAP).toSet()
        ids = next
        platformSetString(KEY, next.joinToString(","))
    }

    /** 卡片用：读到第几页 / 共几页；没有记录返回 null。 */
    fun progressOf(gid: Long): Pair<Int, Int>? {
        ensureProgressLoaded()
        return progress[gid]
    }

    /**
     * 是否真正读完：只有阅读器翻到最后一页才算。
     * 仅打开过详情页（[markRead]）不算读完。
     */
    fun isFinished(gid: Long): Boolean {
        val (page, pages) = progressOf(gid) ?: return false
        return pages > 0 && page >= pages - 1
    }

    /**
     * 记录阅读进度。page 从 0 开始；读到最后一页即视为读完，不再显示进度条。
     * 只保留最近 [PROGRESS_CAP] 个画廊。
     */
    fun recordProgress(gid: Long, page: Int, pages: Int) {
        if (pages <= 0) return
        val safePage = page.coerceIn(0, pages - 1)
        ensureProgressLoaded()
        val current = progress[gid]
        if (current != null && current.first == safePage && current.second == pages) return
        val next = LinkedHashMap(progress)
        next.remove(gid)
        next[gid] = safePage to pages
        val trimmed = if (next.size > PROGRESS_CAP) {
            next.entries.drop(next.size - PROGRESS_CAP).associate { it.key to it.value }
        } else {
            next
        }
        progress = trimmed
        platformSetString(
            PROGRESS_KEY,
            trimmed.entries.joinToString(",") { "${it.key}:${it.value.first}:${it.value.second}" },
        )
    }

    /**
     * 单条清除：删掉某个画廊的「已读」标记与阅读进度。
     *
     * 删除浏览历史时调用，否则列表里的卡片还会挂着「已读完」徽标、
     * 详情页底栏也还会显示「第 N 页」。
     */
    fun clearProgress(gid: Long) {
        ensureLoaded()
        ensureProgressLoaded()
        if (gid in ids) {
            ids = ids - gid
            platformSetString(KEY, ids.joinToString(","))
        }
        if (progress.containsKey(gid)) {
            val next = LinkedHashMap(progress)
            next.remove(gid)
            progress = next
            platformSetString(
                PROGRESS_KEY,
                next.entries.joinToString(",") { "${it.key}:${it.value.first}:${it.value.second}" },
            )
        }
    }

    fun clear() {
        loaded = true
        ids = emptySet()
        platformSetString(KEY, "")
        progressLoaded = true
        progress = emptyMap()
        platformSetString(PROGRESS_KEY, "")
    }
}
