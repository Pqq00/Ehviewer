// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 进程内运行日志。
 *
 * 以前全仓都靠 `println(...)`，日志只进 logcat，用户想反馈问题时导不出来。
 * 这里给出一条旁路：最近的日志留在内存环形缓冲里，同时通过 [fileSink] 交给平台层
 * 写进私有目录的滚动文件；导出诊断包时用 [dump] 取最近若干行。
 *
 * 注意 [fileSink] 由平台层安装（Android 写 `files/logs/eros.log`），
 * core 模块不依赖任何 Android API，桌面端不装也能正常跑。
 */
object AppLog {

    /** 内存里保留的行数。 */
    const val MAX_ENTRIES = 800

    enum class Level(val short: String) {
        DEBUG("D"),
        INFO("I"),
        WARN("W"),
        ERROR("E"),
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** 已经格式化好的日志行，按时间从旧到新。 */
    private val lines = ArrayDeque<String>()

    /** 平台层安装：把一行写进滚动文件。 */
    @Volatile
    var fileSink: ((String) -> Unit)? = null

    /**
     * `true` 表示当前线程正在写「我们自己」的日志行。
     * 安装 stdout 旁路后，[log] 内部的那次 `println` 会再次被旁路捕获，
     * 用它避免同一条日志记录两遍。
     */
    private val teeGuard = ThreadLocal.withInitial { false }

    fun isTeeSuppressed(): Boolean = teeGuard.get() == true

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)

    fun i(tag: String, message: String) = log(Level.INFO, tag, message)

    fun w(tag: String, message: String) = log(Level.WARN, tag, message)

    fun e(tag: String, message: String) = log(Level.ERROR, tag, message)

    fun log(level: Level, tag: String, message: String) {
        val line = "${formatNow()} ${level.short}/$tag: $message"
        recordLine(line)
        // 仍保留 logcat 里的输出（adb logcat -s System.out），方便现场直接看
        teeGuard.set(true)
        try {
            println("[$tag] $message")
        } finally {
            teeGuard.set(false)
        }
    }

    /**
     * 记录一行「别人打印的」输出 —— 由 stdout 旁路调用，已经带上时间戳。
     * 保持原样追加，不再补前缀。
     */
    fun recordStdout(text: String) {
        if (text.isBlank()) return
        recordLine("${formatNow()} O/stdout: $text")
    }

    private fun recordLine(line: String) {
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_ENTRIES) lines.removeFirst()
        }
        fileSink?.let { sink ->
            runCatching { sink(line) }
        }
    }

    fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    /** 最近的日志，最新的在最后；[limit] 只取尾部若干行。 */
    fun dump(limit: Int = MAX_ENTRIES): String {
        val snapshot = snapshot()
        val tail = if (snapshot.size <= limit) snapshot else snapshot.subList(snapshot.size - limit, snapshot.size)
        return tail.joinToString("\n")
    }

    fun clear() = synchronized(lines) { lines.clear() }

    private fun formatNow(): String = timeFormat.format(Date())
}
