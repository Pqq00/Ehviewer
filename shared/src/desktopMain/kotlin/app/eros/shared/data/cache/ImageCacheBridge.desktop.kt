// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data.cache

/** 桌面端图片加载器没有磁盘缓存，统一报 0，清理是空操作。 */
actual fun imageCacheUsage(): ImageCacheUsage? = ImageCacheUsage(memoryBytes = 0L, diskBytes = 0L)

actual fun clearImageCache() = Unit
