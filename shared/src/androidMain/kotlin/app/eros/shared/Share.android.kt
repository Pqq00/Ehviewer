// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import app.eros.shared.settings.AndroidPlatform
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun platformShare(text: String) {
    runCatching {
        val context = AndroidPlatform.appContext
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, "分享画廊").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }.onFailure { println("[Share] failed: ${it.message}") }
}
actual fun platformCopy(text: String) {
    runCatching {
        val context = AndroidPlatform.appContext
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Ehviewer", text))
    }.onFailure { println("[Clipboard] failed: ${it.message}") }
}

actual suspend fun shareImage(bytes: ByteArray, fileName: String): String =
    withContext(Dispatchers.IO) {
        runCatching {
            val context = AndroidPlatform.appContext
            // 分享只写进私有缓存，用完由系统/我们自己清；先清掉上一次的残留，
            // 让 cacheDir/shared 里始终只有这一次要发的那张图。
            val dir = File(context.cacheDir, "shared").apply { if (!exists()) mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, fileName)
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "分享图片").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            "已发送"
        }.getOrElse { "分享失败：${it.message ?: "未知错误"}" }
    }

actual fun encodeJpeg(image: ImageBitmap, quality: Int): ByteArray? = runCatching {
    val bitmap = image.asAndroidBitmap()
    val out = ByteArrayOutputStream()
    val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    if (ok) out.toByteArray() else null
}.getOrNull()
