// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

import com.hippo.ehviewer.util.AppLog

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

actual fun platformShare(text: String) {
    AppLog.i("Share", text)
}
actual fun platformCopy(text: String) {
    runCatching {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
    }
}

/** desktop 没有可分享的目标：把图片导出到 ~/Downloads 并回显路径。 */
actual suspend fun shareImage(bytes: ByteArray, fileName: String): String = runCatching {
    val downloads = File(System.getProperty("user.home"), "Downloads").apply { if (!exists()) mkdirs() }
    val file = File(downloads, fileName)
    file.writeBytes(bytes)
    "已导出：${file.absolutePath}"
}.getOrElse { "分享失败：${it.message ?: "未知错误"}" }

/** desktop 端不支持位图重编码这条路，直接让调用方回落到下载。 */
actual fun encodeJpeg(image: ImageBitmap, quality: Int): ByteArray? = null
