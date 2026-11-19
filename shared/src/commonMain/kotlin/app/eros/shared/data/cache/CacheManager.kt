// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data.cache

import app.eros.shared.media.clearPageCache
import app.eros.shared.media.clearPageLists
import app.eros.shared.media.pageCacheSize
import app.eros.shared.media.pageListCacheSize
import com.hippo.ehviewer.net.HttpCacheBridgeHolder

/**
 * 缓存分区：设置页按这些 scope 展示占用、分别清理。
 *
 * 分层与写入方：
 *  - [HttpResponses]：`core/.../net/HttpCache.kt`，原始响应字节；
 *  - [ParsedModels]：`GalleryCache`，解析后的列表页/详情模型；
 *  - [PageLists]：`media/PageListStore.kt`，每个画廊的页地址列表；
 *  - [ReaderImages]：`media/PageCache.kt`，阅读器页图片文件。
 *
 * 图片加载器（Coil 内存/磁盘）不在这里：它需要 Compose 的 `ImageLoader`，由设置页自己算。
 */
enum class CacheScope(val title: String, val summary: String) {
    HttpResponses("原始响应", "网络返回的页面原文"),
    ParsedModels("列表与详情", "解析后的列表/详情模型"),
    PageLists("页地址", "每个画廊的阅读页地址"),
    ReaderImages("阅读图片", "阅读器缓存的图片文件"),
}

/** 各分区当前占用（字节）。 */
data class CacheUsage(
    val httpResponses: Long = 0L,
    val parsedModels: Long = 0L,
    val pageLists: Long = 0L,
    val readerImages: Long = 0L,
) {
    val total: Long get() = httpResponses + parsedModels + pageLists + readerImages

    fun of(scope: CacheScope): Long = when (scope) {
        CacheScope.HttpResponses -> httpResponses
        CacheScope.ParsedModels -> parsedModels
        CacheScope.PageLists -> pageLists
        CacheScope.ReaderImages -> readerImages
    }
}

/**
 * 缓存分区统计与清理的唯一入口（P4 的雏形）。
 *
 * 规则：读取只做统计、不触发写入；清理按 [CacheScope] 精确删除，**不做整库清空**——
 * 登录态/站点切换这类「整库失效」仍由各自的桥（如 `HttpCacheBridgeHolder.clear()`）
 * 在切换时调用，避免把「用户主动清缓存」做成误伤。
 */
object CacheManager {

    /** 统计各分区占用；单项失败按 0 计，不因为一个分区不可用就让整块显示失败。 */
    suspend fun usage(): CacheUsage = CacheUsage(
        httpResponses = runCatching { HttpCacheBridgeHolder.sizeBytes() }.getOrDefault(0L),
        parsedModels = runCatching { GalleryCache.usageBytes() }.getOrDefault(0L),
        pageLists = runCatching { pageListCacheSize() }.getOrDefault(0L),
        readerImages = runCatching { pageCacheSize() }.getOrDefault(0L),
    )

    suspend fun clear(scope: CacheScope) {
        runCatching {
            when (scope) {
                CacheScope.HttpResponses -> HttpCacheBridgeHolder.clear()
                CacheScope.ParsedModels -> GalleryCache.clearAll()
                CacheScope.PageLists -> clearPageLists()
                CacheScope.ReaderImages -> clearPageCache()
            }
        }
    }
}
