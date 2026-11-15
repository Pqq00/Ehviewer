// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.diag

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import androidx.core.content.FileProvider
import app.eros.shared.BuildInfo
import app.eros.shared.auth.isSignedIn
import app.eros.shared.settings.AndroidPlatform
import app.eros.shared.settings.AppSettings
import com.hippo.ehviewer.net.NetLog
import com.hippo.ehviewer.util.AppLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val context get() = AndroidPlatform.appContext

private const val LOG_DIR = "logs"
private const val LOG_NAME = "eros.log"
private const val LOG_OLD_NAME = "eros.1.log"
private const val LOG_FILE_LIMIT = 512L * 1024L
private const val CRASH_MARKER = "crash-pending.txt"

private val logWriteLock = Any()

@Volatile
private var installed = false

private fun logDir(): File = File(context.filesDir, LOG_DIR).apply { if (!exists()) mkdirs() }

private fun logFile(): File = File(logDir(), LOG_NAME)

/** 追加一行到滚动日志文件；超过 512 KB 就轮转成 `eros.1.log`。 */
private fun appendLogLine(line: String) {
    synchronized(logWriteLock) {
        runCatching {
            val file = logFile()
            if (file.exists() && file.length() > LOG_FILE_LIMIT) {
                val old = File(logDir(), LOG_OLD_NAME)
                if (old.exists()) old.delete()
                file.renameTo(old)
            }
            file.appendText(line + "\n", Charsets.UTF_8)
        }
    }
}

/**
 * L2：把 `System.out` 换成「原样转发 + 收集成行」的流，
 * 现有 120 多处 `println(...)` 一行都不用改就能进到 [AppLog] 与滚动文件。
 */
private class TeeStream(private val delegate: PrintStream) : OutputStream() {

    private val pending = ByteArrayOutputStream()

    override fun write(b: Int) {
        delegate.write(b)
        synchronized(pending) {
            if (b == '\n'.code) {
                emit()
            } else {
                pending.write(b)
            }
        }
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        synchronized(pending) {
            var start = off
            for (i in off until off + len) {
                if (b[i] == '\n'.code.toByte()) {
                    pending.write(b, start, i - start)
                    emit()
                    start = i + 1
                }
            }
            if (start < off + len) pending.write(b, start, off + len - start)
        }
    }

    override fun flush() = delegate.flush()

    private fun emit() {
        val text = pending.toString("UTF-8")
        pending.reset()
        if (AppLog.isTeeSuppressed()) return
        AppLog.recordStdout(text)
    }
}

private fun installStdoutTee() {
    runCatching {
        val origin = System.out
        System.setOut(PrintStream(TeeStream(origin), true, "UTF-8"))
    }
}

private fun writeCrashReport(thread: Thread, throwable: Throwable): File? = runCatching {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val file = File(logDir(), "crash-$stamp.txt")
    val text = buildString {
        appendLine("# Ehviewer 崩溃报告")
        appendLine("# 时间 ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("# 版本 ${BuildInfo.version} (${BuildInfo.versionCode})  ${context.packageName}")
        appendLine("# 系统 Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})  ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("# 线程 ${thread.name} (id=${thread.id})")
        appendLine()
        appendLine("## 异常")
        appendLine(throwable.stackTraceToString())
        appendLine()
        appendLine("## 崩溃前运行日志")
        appendLine(AppLog.dump(300))
    }
    file.writeText(text, Charsets.UTF_8)
    File(logDir(), CRASH_MARKER).writeText(file.name, Charsets.UTF_8)
    file
}.getOrNull()

private fun installCrashHandler() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        writeCrashReport(thread, throwable)
        previous?.uncaughtException(thread, throwable)
    }
}

actual fun installDiagnostics() {
    if (installed) return
    installed = true
    AppLog.fileSink = { line -> appendLogLine(line) }
    installStdoutTee()
    installCrashHandler()
}

private fun crashMarker(): File = File(logDir(), CRASH_MARKER)

private fun latestCrashFile(): File? {
    val marker = crashMarker()
    if (!marker.exists()) return null
    val name = runCatching { marker.readText(Charsets.UTF_8).trim() }.getOrNull()
    if (name.isNullOrBlank()) return null
    val file = File(logDir(), name)
    return file.takeIf { it.exists() }
}

actual fun pendingCrashSummary(): String? {
    val file = latestCrashFile() ?: return null
    val head = runCatching {
        file.useLines { lines ->
            lines.take(12).filter { it.startsWith("## 异常") || it.contains("Exception") || it.contains("Error") }
                .joinToString("\n")
        }
    }.getOrNull()
    val whenText = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(file.lastModified()))
    return buildString {
        append("上次启动崩溃于 $whenText")
        if (!head.isNullOrBlank()) append("：\n").append(head)
    }
}

actual fun acknowledgeCrash() {
    runCatching { crashMarker().delete() }
}

private val cookiePattern = Regex(
    """(?i)\b(ipb_member_id|ipb_pass_hash|igneous|sk|sp)\b\s*[=:]\s*["']?([A-Za-z0-9_\-]{4,})""",
)
private val tokenPattern = Regex("""(/g/\d+/)([0-9a-fA-F]{8,})""")

private fun sanitize(text: String): String = text
    .replace(cookiePattern) { "${it.groupValues[1]}=***" }
    .replace(tokenPattern) { "${it.groupValues[1]}***" }

private fun readLogcat(maxLines: Int = 2000): String = runCatching {
    val pid = Process.myPid()
    val process = Runtime.getRuntime().exec(
        arrayOf("logcat", "-d", "-v", "threadtime", "-t", maxLines.toString(), "--pid=$pid"),
    )
    val text = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor()
    process.destroy()
    text.ifBlank { "（系统没有返回本进程的 logcat，可能被 ROM 限制）" }
}.getOrElse { "（读取系统日志失败：${it.message ?: it.javaClass.simpleName}）" }

private fun buildBundle(): String {
    val now = Date()
    val crash = latestCrashFile()

    // 头部只留四行关键信息：生成时间、版本、系统、登录与里站。
    // 设备名、ABI、屏幕、语言时区、主题导航这些对定位问题没什么帮助，反而让头部变长。
    return sanitize(buildString {
        appendLine("# Ehviewer 诊断日志")
        appendLine("# 生成时间 ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)}")
        appendLine("# 版本 ${BuildInfo.version} (${BuildInfo.versionCode})")
        appendLine("# 系统 Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine(
            "# 登录 ${if (isSignedIn()) "已登录" else "未登录"}  " +
                "里站 ${if (AppSettings.exHentai) "开" else "关"}",
        )
        appendLine()
        appendLine("## 最近运行日志")
        appendLine(AppLog.dump())
        appendLine()
        appendLine("## 网络记录")
        appendLine(if (AppSettings.netLogEnabled) NetLog.dump() else "（未开启网络日志）")
        if (crash != null) {
            appendLine()
            appendLine("## 上次崩溃")
            appendLine(runCatching { crash.readText(Charsets.UTF_8) }.getOrDefault("（崩溃文件读取失败）"))
        }
        appendLine()
        appendLine("## logcat（本进程，最近 2000 行）")
        appendLine(readLogcat())
    })
}

private fun saveToDownloads(content: String, name: String): String {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val file = File(dir, name)
            file.writeText(content, Charsets.UTF_8)
            "已导出到 ${file.absolutePath}"
        }
    } catch (t: Throwable) {
        "导出失败：${t.message ?: t.javaClass.simpleName}"
    }
}

private fun shareBundle(content: String, name: String): String {
    return try {
        val dir = File(context.cacheDir, "diag").apply { if (!exists()) mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, name)
        file.writeText(content, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Ehviewer 诊断日志")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "分享日志").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        "已打开分享面板"
    } catch (t: Throwable) {
        "分享失败：${t.message ?: t.javaClass.simpleName}"
    }
}

actual suspend fun exportDiagnostics(share: Boolean): String = withContext(Dispatchers.IO) {
    val name = "eros-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
    val content = runCatching { buildBundle() }.getOrElse {
        "# Ehviewer 诊断日志\n# 生成失败：${it.message ?: it.javaClass.simpleName}"
    }
    val result = if (share) shareBundle(content, name) else saveToDownloads(content, name)
    // 崩溃段已经写进诊断包了，清掉标记，避免每次启动都再提示一遍。
    acknowledgeCrash()
    result
}
