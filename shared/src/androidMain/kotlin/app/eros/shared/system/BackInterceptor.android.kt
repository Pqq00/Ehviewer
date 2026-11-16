// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.system

import com.hippo.ehviewer.util.AppLog

import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import app.eros.shared.settings.AndroidPlatform

private var registered: OnBackInvokedCallback? = null
private var registeredDispatcher: OnBackInvokedDispatcher? = null

actual fun setPlainBackInterceptor(enabled: Boolean, onBack: (() -> Boolean)?) {
    if (Build.VERSION.SDK_INT < 33) return
    val activity = AndroidPlatform.currentActivity ?: return
    val dispatcher = activity.onBackInvokedDispatcher

    if (!enabled || onBack == null) {
        registered?.let { cb -> runCatching { dispatcher.unregisterOnBackInvokedCallback(cb) } }
        registered = null
        registeredDispatcher = null
        return
    }

    if (registered != null && registeredDispatcher === dispatcher) return

    registered?.let { cb -> runCatching { dispatcher.unregisterOnBackInvokedCallback(cb) } }
    val callback = OnBackInvokedCallback {
        // 交给 Compose 侧导航弹栈；栈底时退出 Activity
        val handled = runCatching { onBack.invoke() }.getOrDefault(false)
        if (!handled) runCatching { activity.finish() }
    }
    // 用 OVERLAY 优先级压过 miuix-nav 注册的动画回调
    runCatching {
        dispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback,
        )
        registered = callback
        registeredDispatcher = dispatcher
        AppLog.i("Back", "plain interceptor installed")
    }
}
