// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data.cache

/** 图片加载器（Coil）缓存占用：内存 + 磁盘。 */
data class ImageCacheUsage(
    val memoryBytes: Long,
    val diskBytes: Long,
) {
    val totalBytes: Long get() = memoryBytes + diskBytes
}

/** 读取图片加载器缓存占用；平台不支持时返回 null。 */
expect fun imageCacheUsage(): ImageCacheUsage?

/** 清空图片加载器的内存与磁盘缓存。 */
expect fun clearImageCache()
