// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 调试用网络日志（环形缓冲）。
 *
 * 设计目标：出问题时能回答"到底发了什么请求、多久、返回多大、错在哪"，
 * 而不是只看到一个 "Read timed out"。
 *
 * 只有 [enabled] 为 true 时才记录，避免正常使用时占内存。
 * 注意：这里用 SimpleDateFormat 而不是 java.time —— Android minSdk 24，java.time 要 26+。
 */
object NetLog {

    const val MAX_ENTRIES = 200

    @Volatile
    var enabled: Boolean = false

    data class Entry(
        val epochMs: Long,
        val method: String,
        val url: String,
        val status: Int,
        val durationMs: Long,
        val bytes: Long,
        val error: String?,
    )

    private val entries = ArrayDeque<Entry>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fullFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun formatTime(epochMs: Long): String = timeFormat.format(Date(epochMs))

    /** 一行可读文本，UI 与导出共用。 */
    fun formatEntry(e: Entry): String {
        val status = if (e.status > 0) e.status.toString() else "ERR"
        val err = if (e.error.isNullOrBlank()) "" else "  <= ${e.error}"
        return "${formatTime(e.epochMs)} ${e.method} $status ${e.durationMs}ms ${e.bytes}B ${e.url}$err"
    }

    fun log(method: String, url: String, status: Int, durationMs: Long, bytes: Long, error: String?) {
        if (!enabled) return
        val entry = Entry(System.currentTimeMillis(), method, url, status, durationMs, bytes, error)
        synchronized(entries) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
            }
        }
    }

    fun snapshot(): List<Entry> = synchronized(entries) { entries.toList() }

    fun clear() = synchronized(entries) { entries.clear() }

    /** 导出为纯文本。 */
    fun dump(): String = buildString {
        appendLine("# eros netlog")
        appendLine("# generated ${fullFormat.format(Date())}")
        appendLine("# entries ${snapshot().size}")
        appendLine()
        snapshot().forEach { appendLine(formatEntry(it)) }
    }
}
