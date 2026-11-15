// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.download

import android.content.Intent
import app.eros.shared.settings.AndroidPlatform

/**
 * 通过类名启动前台服务，避免 shared 模块反向依赖 app 模块。
 */
actual object DownloadForeground {

    private const val SERVICE_CLASS = "app.eros.android.DownloadService"
    const val ACTION_START = "app.eros.download.START"
    const val ACTION_STOP = "app.eros.download.STOP"

    actual fun start() {
        runCatching {
            val context = AndroidPlatform.appContext
            val intent = Intent(ACTION_START).setClassName(context, SERVICE_CLASS)
            context.startForegroundService(intent)
            println("[Download] foreground service start")
        }.onFailure { println("[Download] start service failed: ${it.message}") }
    }

    actual fun stop() {
        runCatching {
            val context = AndroidPlatform.appContext
            val intent = Intent(ACTION_STOP).setClassName(context, SERVICE_CLASS)
            context.startService(intent)
            println("[Download] foreground service stop")
        }.onFailure { println("[Download] stop service failed: ${it.message}") }
    }
}
