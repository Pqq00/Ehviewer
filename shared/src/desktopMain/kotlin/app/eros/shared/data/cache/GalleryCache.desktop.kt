// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

actual fun createGalleryCacheStore(): GalleryCacheStore = MemoryGalleryCacheStore()

/** 桌面端 P0 缓存：进程内内存实现（测试/调试用，不落盘）。 */
internal class MemoryGalleryCacheStore : GalleryCacheStore {

    private data class PageEntry(val page: CachedPage)
    private data class DetailEntry(val record: DetailRecord)

    private val mutex = Mutex()
    private val pages = LinkedHashMap<String, PageEntry>()
    private val details = LinkedHashMap<Long, DetailEntry>()

    override suspend fun loadPage(key: String, index: Int, maxAgeMs: Long): CachedPage? =
        mutex.withLock {
            val entry = pages["$key#$index"] ?: return@withLock null
            if (GalleryCache.now() - entry.page.fetchedAt > maxAgeMs) return@withLock null
            entry.page
        }

    override suspend fun savePage(key: String, index: Int, page: CachedPage) = mutex.withLock {
        pages["$key#$index"] = PageEntry(page)
    }

    override suspend fun loadDetail(gid: Long, token: String, maxAgeMs: Long): DetailRecord? =
        mutex.withLock {
            val entry = details[gid] ?: return@withLock null
            if (entry.record.token != token) return@withLock null
            if (GalleryCache.now() - entry.record.fetchedAt > maxAgeMs) return@withLock null
            entry.record
        }

    override suspend fun saveDetail(record: DetailRecord) = mutex.withLock {
        details[record.gid] = DetailEntry(record)
    }

    override suspend fun clearListPages() = mutex.withLock {
        pages.clear()
        Unit
    }

    override suspend fun clear() = mutex.withLock {
        pages.clear()
        details.clear()
        Unit
    }

    override suspend fun prune(now: Long, pageMaxAgeMs: Long, detailMaxAgeMs: Long) = mutex.withLock {
        pages.entries.removeAll { now - it.value.page.fetchedAt > pageMaxAgeMs }
        details.entries.removeAll { now - it.value.record.fetchedAt > detailMaxAgeMs }
        Unit
    }
}
