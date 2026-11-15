// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.settings

import java.io.File

actual object SettingsStorage {

    private fun file(): File = File(AndroidPlatform.appContext.filesDir, "settings.json")

    actual fun read(): String? = runCatching {
        val f = file()
        if (f.exists() && f.length() > 0) f.readText() else null
    }.getOrNull()

    actual fun write(text: String) {
        runCatching {
            val f = file()
            val tmp = File(f.parentFile, "settings.json.tmp")
            tmp.writeText(text)
            if (f.exists()) f.delete()
            tmp.renameTo(f)
        }
    }
}
