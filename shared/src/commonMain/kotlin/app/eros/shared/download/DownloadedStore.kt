// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.download

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 已完整下载的画廊 gid 集合，供列表卡片显示「已下载」角标。
 *
 * 数据源是 [DownloadManager.tasks]（进程内的 StateFlow），
 * 不再在主线程读取磁盘清单，避免列表滚动时做 IO。
 */
object DownloadedStore {

    private var ids by mutableStateOf<Set<Long>>(emptySet())
    private var loaded = false

    /** 由 [DownloadManager] 在恢复/完成/删除后调用，避免列表卡片在主线程扫磁盘。 */
    fun update(tasks: List<DownloadManifest>) {
        ids = tasks.asSequence()
            .filter { it.status == DownloadStatus.DONE }
            .map { it.gid }
            .toSet()
        loaded = true
    }

    private fun ensureLoaded() {
        if (loaded) return
        update(DownloadManager.tasks.value)
    }

    fun isDownloaded(gid: Long): Boolean {
        ensureLoaded()
        return gid in ids
    }

    fun refresh() {
        update(DownloadManager.tasks.value)
    }
}
