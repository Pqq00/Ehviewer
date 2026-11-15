// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.tags

import com.hippo.ehviewer.client.EhTagDatabase
import java.io.File

private fun file(): File = File(System.getProperty("user.home"), ".eros-tagdb.json")

actual fun saveTagDatabase(json: String) {
    runCatching { file().writeText(json) }
}

actual fun loadCachedTagDatabase(): String? = runCatching {
    val f = file()
    if (f.exists() && f.length() > 0) f.readText() else null
}.getOrNull()

actual fun initTagDatabase(): Int {
    val json = loadCachedTagDatabase() ?: return 0
    return EhTagDatabase.load(json)
}
