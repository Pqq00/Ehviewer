// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.diag

import com.hippo.ehviewer.util.AppLog

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.eros.shared.settings.AndroidPlatform
import com.hippo.ehviewer.net.NetLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun setNetLogEnabled(enabled: Boolean) {
    NetLog.enabled = enabled
    if (enabled) NetLog.clear()
    AppLog.i("NetLog", "enabled=$enabled")
}

actual fun netLogEntries(): List<NetLogUi> =
    NetLog.snapshot().asReversed().map { e ->
        NetLogUi(
            time = NetLog.formatTime(e.epochMs),
            line = NetLog.formatEntry(e),
            isError = e.error != null || e.status !in 200..299,
        )
    }

actual fun clearNetLog() = NetLog.clear()

actual fun exportNetLog(): String {
    val name = "eros-netlog-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
    val content = NetLog.dump()
    val context = AndroidPlatform.appContext

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+：走 MediaStore 写公共 Download 目录，不需要权限
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return "导出失败：无法创建文件条目"
            resolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: return "导出失败：无法写入"
            "已导出到 Download/$name"
        } else {
            // Android 9 及以下：退回应用专属目录（公共目录需要 WRITE_EXTERNAL_STORAGE）
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val file = File(dir, name)
            file.writeText(content, Charsets.UTF_8)
            "已导出到 ${file.absolutePath}"
        }
    } catch (t: Throwable) {
        "导出失败：${t.message ?: t.javaClass.simpleName}"
    }
}
