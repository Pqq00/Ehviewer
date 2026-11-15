// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

import androidx.compose.ui.graphics.ImageBitmap

/** 调用系统分享。 */
expect fun platformShare(text: String)
/** 复制文本到系统剪贴板。 */
expect fun platformCopy(text: String)

/**
 * 把一张图片交给系统分享面板（不落相册）。
 *
 * @param bytes 图片字节，按 [fileName] 的扩展名决定 MIME
 * @return 给用户的提示文案
 */
expect suspend fun shareImage(bytes: ByteArray, fileName: String): String

/**
 * 把已经解码的位图就地编码成 JPEG。
 *
 * 只在拿不到原始字节（缓存被裁掉、页面是从内存位图来的）时作为兜底，
 * 属于二次编码，质量有轻微损失；桌面端没有这条路径，返回 null。
 */
expect fun encodeJpeg(image: ImageBitmap, quality: Int = 95): ByteArray?
