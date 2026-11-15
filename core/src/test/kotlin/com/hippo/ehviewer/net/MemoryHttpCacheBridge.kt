// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import java.util.concurrent.ConcurrentHashMap

/**
 * 测试用内存实现：[HttpCacheBridge] 的进程内替身。
 *
 * 原先放在 main 源集，只有测试引用，已下移到测试源集避免污染发布产物。
 */
class MemoryHttpCacheBridge : HttpCacheBridge {
    private data class Entry(val response: CachedHttpResponse, val createdAt: Long)

    private val map = ConcurrentHashMap<String, Entry>()

    override fun get(url: String, now: Long): CachedHttpResponse? {
        val entry = map[url] ?: return null
        if (entry.response.expiresAt <= now) {
            map.remove(url, entry)
            return null
        }
        return entry.response
    }

    override fun put(url: String, response: CachedHttpResponse) {
        map[url] = Entry(response, System.currentTimeMillis())
    }

    override fun removePrefix(prefix: String) {
        map.keys.removeIf { it.startsWith(prefix) }
    }

    override fun clear() {
        map.clear()
    }
}
