// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.platform

import java.io.File

internal actual fun storagePath(area: StorageArea): String = when (area) {
    StorageArea.DownloadsBase -> File(System.getProperty("user.home"), ".eros").absolutePath
    StorageArea.PageLists -> File(System.getProperty("java.io.tmpdir"), "eros-page-lists").absolutePath
}
