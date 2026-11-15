// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.media

/**
 * 阅读器页图片的磁盘缓存（按 gid + 页号存文件）。
 *
 * 容量上限 [PAGE_CACHE_LIMIT_BYTES]（500 MB）：写入后如果超限，按目录最后修改时间
 * 淘汰最旧的 gid（LRU）。
 */
expect fun readPageCache(gid: Long, index: Int): ByteArray?

expect fun writePageCache(gid: Long, index: Int, bytes: ByteArray)

/** 阅读器页缓存当前占用字节数。 */
expect fun pageCacheSize(): Long

/** 清空阅读器页缓存。 */
expect fun clearPageCache()

/** 阅读器页缓存上限：500 MB。 */
const val PAGE_CACHE_LIMIT_BYTES: Long = 500L * 1024L * 1024L
