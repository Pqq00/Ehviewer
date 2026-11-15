// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.media

/**
 * 阅读器页地址列表的磁盘缓存。
 *
 * 抓一次全部页地址可能要好几十个请求，所以按 gid 落盘：
 * 第二次打开同一画廊直接读盘，拖进度条立即可用。
 *
 * 文件格式（纯文本，避免引入 JSON 依赖）：
 * ```
 * v1
 * <savedAtEpochMillis>
 * <totalPages>
 * <url1>
 * <url2>
 * ...
 * ```
 */
data class PageListCache(
    val savedAt: Long,
    val totalPages: Int,
    val urls: List<String>,
)

/** 读取缓存的页地址；没有、不合法或已过期（[maxAgeMs]）返回 null。 */
expect fun readPageList(gid: Long, maxAgeMs: Long = Long.MAX_VALUE): PageListCache?

/** 写入缓存的页地址。 */
expect fun writePageList(gid: Long, totalPages: Int, urls: List<String>)

/** 清空页地址缓存。 */
expect fun clearPageLists()

/** 页地址缓存占用字节数。 */
expect fun pageListCacheSize(): Long
