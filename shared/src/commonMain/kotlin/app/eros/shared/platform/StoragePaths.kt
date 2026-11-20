// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.platform

/**
 * 大文件存储位置（绝对路径）。
 *
 * 只有 Android / JVM 两个目标，共享实现放在 `jvmSharedMain`；这里只把「路径从哪来」
 * 交给平台：Android 走应用私有目录，桌面走用户目录 / 临时目录。
 */
internal enum class StorageArea {
    /** 下载根目录的上一级：Android 是 filesDir，桌面是 ~/.eros。 */
    DownloadsBase,

    /** 阅读页地址缓存目录。 */
    PageLists,
}

internal expect fun storagePath(area: StorageArea): String
