// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.diag

/**
 * 诊断能力（L1/L2/L3）的跨平台入口。
 *
 * - L2：安装 stdout 旁路 + 日志滚动文件，把 `AppLog` 与仍在走 stdout 的输出收进私有目录；
 * - L3：安装未捕获异常处理器，崩溃时落一份 `crash-<时间>.txt`，下次启动提示并能一起导出；
 * - L1：[exportDiagnostics] 生成一份可直接发给开发者的诊断包。
 */

/** 安装诊断能力（幂等，可重复调用）。 */
expect fun installDiagnostics()

/** 上次进程崩溃的摘要；没有崩溃则返回 null。 */
expect fun pendingCrashSummary(): String?

/** 用户已看过/导出过崩溃日志后调用，清掉提示标记。 */
expect fun acknowledgeCrash()

/**
 * 导出诊断包。
 *
 * @param share `false` 写到「下载」目录并返回结果文案；`true` 打开系统分享面板。
 */
expect suspend fun exportDiagnostics(share: Boolean): String
