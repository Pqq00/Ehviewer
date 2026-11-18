// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * `Retry-After` 头的统一解析。
 *
 * RFC 9110 允许两种写法：delta-seconds（`120`）与 HTTP-date
 * （`Sun, 06 Nov 1994 08:49:37 GMT`；老客户端还可能发 RFC 850 / asctime）。
 * 旧实现只做 `toLongOrNull()`，遇到 Cloudflare 常见的 HTTP-date 就当成
 * 「服务器没给提示」按本地退避走 —— 服务器要求的等待时间被整个忽略。
 */
internal object RetryAfter {

    /** IMF-fixdate（RFC 1123 的实际格式）：`Sun, 06 Nov 1994 08:49:37 GMT`。 */
    private val IMF_FIXDATE = DateTimeFormatter.RFC_1123_DATE_TIME

    /** 宽容变体：日不补零（`Sun, 6 Nov 1994 …`），少数服务端会这样发。 */
    private val IMF_FIXDATE_LOOSE =
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.US)

    /**
     * RFC 850：`Sunday, 06-Nov-94 08:49:37 GMT`。
     *
     * 两位年份按 RFC 9110 的「50 年窗口」处理：`appendValueReduced(…, 1970)` 让
     * 70–99 落到 19xx、00–69 落到 20xx，避免把 1994 解析成 2094。
     */
    private val RFC_850 = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("EEEE, dd-MMM-")
        .appendValueReduced(ChronoField.YEAR, 2, 2, 1970)
        .appendPattern(" HH:mm:ss zzz")
        .toFormatter(Locale.US)

    /** asctime：`Sun Nov  6 08:49:37 1994`，无时区，按 GMT 解释。 */
    private val ASCTIME = Regex(
        """^[A-Za-z]{3} ([A-Za-z]{3}) +(\d{1,2}) (\d{2}):(\d{2}):(\d{2}) (\d{4})$""",
    )

    /** asctime 只给月份缩写（`Nov`），而 [java.time.Month] 的枚举名是全称，需要自己映射。 */
    private val MONTH_ABBREVIATIONS =
        listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    /**
     * 解析成「还需要等多少毫秒」；无法解析返回 null。
     *
     * - 数字形式原样换算（`0` 合法，负数按 0）；
     * - 日期形式按 [now] 求差，已经过期按 0（立即重试）。
     *
     * 上限由调用方决定：拦截器会把等待钳到 10 秒，免得服务端写一个
     * 一小时后的日期就把请求挂死。
     */
    fun parseMillis(raw: String?, now: Long = System.currentTimeMillis()): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null

        value.toLongOrNull()?.let { seconds -> return (seconds * 1000L).coerceAtLeast(0L) }

        val epochMillis = parseHttpDate(value) ?: return null
        return (epochMillis - now).coerceAtLeast(0L)
    }

    private fun parseHttpDate(value: String): Long? {
        for (formatter in listOf(IMF_FIXDATE, IMF_FIXDATE_LOOSE, RFC_850)) {
            runCatching { ZonedDateTime.parse(value, formatter) }
                .getOrNull()
                ?.let { return it.toInstant().toEpochMilli() }
        }

        val match = ASCTIME.matchEntire(value) ?: return null
        return runCatching {
            val (month, day, hour, minute, second, year) = match.destructured
            val monthIndex = MONTH_ABBREVIATIONS.indexOf(month.uppercase(Locale.US))
            require(monthIndex >= 0) { "未知月份缩写: $month" }
            val dateTime = LocalDateTime.of(
                year.toInt(),
                monthIndex + 1,
                day.toInt(),
                hour.toInt(),
                minute.toInt(),
                second.toInt(),
            )
            dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()
    }
}
