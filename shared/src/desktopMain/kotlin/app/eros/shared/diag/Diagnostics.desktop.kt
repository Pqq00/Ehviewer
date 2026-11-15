// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.diag

import app.eros.shared.BuildInfo
import app.eros.shared.auth.isSignedIn
import app.eros.shared.settings.AppSettings
import com.hippo.ehviewer.net.NetLog
import com.hippo.ehviewer.util.AppLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 桌面端只做最小实现：日志留在控制台，导出写进 `~/Downloads`。
 * 崩溃捕获与 stdout 旁路只在 Android 上有实际意义（桌面直接看终端）。
 */
actual fun installDiagnostics() = Unit

actual fun pendingCrashSummary(): String? = null

actual fun acknowledgeCrash() = Unit

actual suspend fun exportDiagnostics(share: Boolean): String = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(System.getProperty("user.home"), "Downloads").apply { if (!exists()) mkdirs() }
        val name = "eros-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val file = File(dir, name)
        val text = buildString {
            // 与 Android 保持同一套头部：只留生成时间、版本、系统、登录与里站。
            appendLine("# Ehviewer 诊断日志")
            appendLine("# 生成时间 ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("# 版本 ${BuildInfo.version} (${BuildInfo.versionCode})")
            appendLine("# 系统 desktop ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
            appendLine("# 登录 ${if (isSignedIn()) "已登录" else "未登录"}  里站 ${if (AppSettings.exHentai) "开" else "关"}")
            appendLine()
            appendLine("## 最近运行日志")
            appendLine(AppLog.dump())
            appendLine()
            appendLine("## 网络记录")
            appendLine(NetLog.dump())
        }
        file.writeText(text, Charsets.UTF_8)
        "已导出到 ${file.absolutePath}"
    }.getOrElse { "导出失败：${it.message ?: it.javaClass.simpleName}" }
}
