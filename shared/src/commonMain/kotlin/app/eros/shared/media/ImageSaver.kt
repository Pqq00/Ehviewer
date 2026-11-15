// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.media

/**
 * 把图片字节存到系统相册/下载目录，返回给用户看的结果文案。
 * Android 走 MediaStore（Pictures/eros），桌面写 ~/Downloads。
 */
expect fun saveImage(bytes: ByteArray, fileName: String): String
