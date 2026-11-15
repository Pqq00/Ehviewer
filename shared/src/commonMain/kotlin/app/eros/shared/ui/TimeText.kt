// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import app.eros.shared.currentTimeMillis

/** 相对时间：刚刚 / x 分钟前 / x 小时前 / x 天前 / 日期。 */
internal fun relativeTime(timeMillis: Long): String {
    val diff = currentTimeMillis() - timeMillis
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 24 * 3_600_000L -> "${diff / 3_600_000} 小时前"
        diff < 30 * 24 * 3_600_000L -> "${diff / (24 * 3_600_000L)} 天前"
        else -> {
            val days = timeMillis / 60_000 / (24 * 60)
            val (year, month, day) = civilFromDays(days)
            val mm = if (month < 10) "0$month" else "$month"
            val dd = if (day < 10) "0$day" else "$day"
            "$year-$mm-$dd"
        }
    }
}

/** 从 1970-01-01 起的天数换算成公历年月日（不引入 kotlinx-datetime）。 */
internal fun civilFromDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
    val z = daysSinceEpoch + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    return Triple((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt())
}
