// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.media

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.eros.shared.settings.AndroidPlatform
import java.io.File

actual fun saveImage(bytes: ByteArray, fileName: String): String {
    val context = AndroidPlatform.appContext
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/eros")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return "保存失败：无法创建相册条目"
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return "保存失败：无法写入"
            "已保存到 相册/eros/$fileName"
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            val file = File(dir, fileName)
            file.writeBytes(bytes)
            "已保存到 ${file.absolutePath}"
        }
    } catch (t: Throwable) {
        "保存失败：${t.message ?: t.javaClass.simpleName}"
    }
}
