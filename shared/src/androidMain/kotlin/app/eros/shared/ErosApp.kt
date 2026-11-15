// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

import android.app.Application
import app.eros.shared.data.cache.AndroidHttpCacheBridge
import app.eros.shared.download.DownloadManager
import app.eros.shared.settings.AndroidPlatform
import com.hippo.ehviewer.net.HttpCacheBridgeHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 进程入口：安装网络层缓存。
 *
 * 放在 Application 而不是 Activity，保证 DownloadService 冷启动时缓存也可用。
 */
class ErosApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AndroidPlatform.appContext = this
        val httpCache = AndroidHttpCacheBridge(this)
        HttpCacheBridgeHolder.bridge = httpCache
        httpCache.pruneExpired()
        // 预热下载队列恢复：放在 IO，进下载页时通常已 ready
        appScope.launch { DownloadManager.ensureInitialized() }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
