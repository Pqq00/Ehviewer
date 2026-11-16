// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.diag

import com.hippo.ehviewer.util.AppLog

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
    return try {
        val dir = File(System.getProperty("user.home"), "Downloads")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        file.writeText(NetLog.dump(), Charsets.UTF_8)
        "已导出到 ${file.absolutePath}"
    } catch (t: Throwable) {
        "导出失败：${t.message ?: t.javaClass.simpleName}"
    }
}
