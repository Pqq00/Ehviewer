// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.download

/**
 * 下载前台服务（通知栏进度 + 后台保活）。
 *
 * 平台实现：
 *  - Android：启动前台 Service，常驻通知显示进度；有下载时保活进程
 *  - 桌面：空实现
 */
expect object DownloadForeground {

    /** 队列里有任务开始下载时调用（幂等）。 */
    fun start()

    /** 空闲/全部结束时调用。 */
    fun stop()
}
