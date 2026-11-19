// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data.cache

import app.eros.shared.settings.AndroidPlatform
import coil3.SingletonImageLoader

actual fun imageCacheUsage(): ImageCacheUsage? = runCatching {
    val loader = SingletonImageLoader.get(AndroidPlatform.appContext)
    ImageCacheUsage(
        memoryBytes = loader.memoryCache?.size ?: 0L,
        diskBytes = loader.diskCache?.size ?: 0L,
    )
}.getOrNull()

actual fun clearImageCache() {
    runCatching {
        val loader = SingletonImageLoader.get(AndroidPlatform.appContext)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }
}
