// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.diag

/** 网络日志的一行（已格式化）。 */
data class NetLogUi(
    val time: String,
    val line: String,
    val isError: Boolean,
)

/** 打开/关闭网络日志。开启时会清空旧日志，保证一次调试对应一份干净的记录。 */
expect fun setNetLogEnabled(enabled: Boolean)

/** 最新在前的日志快照。 */
expect fun netLogEntries(): List<NetLogUi>

expect fun clearNetLog()

/** 导出到系统 Download 目录，返回给用户看的结果描述（成功路径或失败原因）。 */
expect fun exportNetLog(): String
