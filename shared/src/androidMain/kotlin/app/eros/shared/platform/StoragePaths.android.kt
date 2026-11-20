// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.platform

import app.eros.shared.settings.AndroidPlatform
import java.io.File

internal actual fun storagePath(area: StorageArea): String = when (area) {
    StorageArea.DownloadsBase -> AndroidPlatform.appContext.filesDir.absolutePath
    StorageArea.PageLists -> File(AndroidPlatform.appContext.filesDir, "page_lists").absolutePath
}
