// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.media

import java.io.File

actual fun saveImage(bytes: ByteArray, fileName: String): String {
    return try {
        val dir = File(System.getProperty("user.home"), "Downloads")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        "已保存到 ${file.absolutePath}"
    } catch (t: Throwable) {
        "保存失败：${t.message ?: t.javaClass.simpleName}"
    }
}
