// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.settings

import java.util.prefs.Preferences

private val node: Preferences by lazy { Preferences.userRoot().node("app/eros") }

actual fun platformGetBool(key: String, default: Boolean): Boolean =
    runCatching { node.getBoolean(key, default) }.getOrDefault(default)

actual fun platformSetBool(key: String, value: Boolean) {
    runCatching { node.putBoolean(key, value) }
}

actual fun platformGetString(key: String, default: String): String =
    runCatching { node.get(key, default) }.getOrDefault(default)

actual fun platformSetString(key: String, value: String) {
    runCatching { node.put(key, value) }
}

actual fun platformGetInt(key: String, default: Int): Int =
    runCatching { node.getInt(key, default) }.getOrDefault(default)

actual fun platformSetInt(key: String, value: Int) {
    runCatching { node.putInt(key, value) }
}

actual fun platformHasKey(key: String): Boolean =
    runCatching { node.get(key, null) != null }.getOrDefault(false)