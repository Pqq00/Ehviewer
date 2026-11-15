// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data.cache

import app.eros.shared.data.createEhApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 详情预取：列表可见项静止一小段时间后，后台把详情（含评论/首屏预览）拉回来，
 * 点进卡片时直接命中 [DetailMemoryCache]。
 * 失败静默，不影响正常打开；重复请求由 EhEngine 的 GET single-flight 合并。
 */
object DetailPrefetcher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api by lazy { createEhApi() }
    private val mutex = Mutex()
    private val inFlight = mutableSetOf<String>()

    suspend fun prefetch(gid: Long, token: String) {
        if (DetailMemoryCache.isFresh(gid, token)) return
        val key = "$gid:$token"
        mutex.withLock { if (!inFlight.add(key)) return }
        scope.launch {
            try {
                runCatching {
                    val d = api.detail(gid, token, forceRefresh = false)
                    GalleryCache.saveDetail(d, token)
                    DetailMemoryCache.put(d, token)
                    println("[DetailPrefetch] done gid=$gid")
                }.onFailure {
                    println("[DetailPrefetch] skip gid=$gid: ${it.message}")
                }
            } finally {
                mutex.withLock { inFlight.remove(key) }
            }
        }
    }
}
