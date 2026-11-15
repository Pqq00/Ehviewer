// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.settings

import android.annotation.SuppressLint
import android.content.Context

/** 由 MainActivity 在 setContent 之前注入。 */
object AndroidPlatform {
    @SuppressLint("StaticFieldLeak")
    lateinit var appContext: Context

    /** 当前 Activity，用于接管返回（预测式返回开关）。 */
    @SuppressLint("StaticFieldLeak")
    var currentActivity: android.app.Activity? = null
}

private fun prefs() = AndroidPlatform.appContext.getSharedPreferences("eros_settings", Context.MODE_PRIVATE)

actual fun platformGetBool(key: String, default: Boolean): Boolean =
    runCatching { prefs().getBoolean(key, default) }.getOrDefault(default)

actual fun platformSetBool(key: String, value: Boolean) {
    runCatching { prefs().edit().putBoolean(key, value).apply() }
}

actual fun platformGetString(key: String, default: String): String =
    runCatching { prefs().getString(key, default) ?: default }.getOrDefault(default)

actual fun platformSetString(key: String, value: String) {
    runCatching { prefs().edit().putString(key, value).apply() }
}

actual fun platformGetInt(key: String, default: Int): Int =
    runCatching { prefs().getInt(key, default) }.getOrDefault(default)

actual fun platformSetInt(key: String, value: Int) {
    runCatching { prefs().edit().putInt(key, value).apply() }
}

actual fun platformHasKey(key: String): Boolean =
    runCatching { prefs().contains(key) }.getOrDefault(false)