// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.media

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 把图片字节解码成 Compose 位图（失败返回 null）。
 *
 * [maxWidthPx] > 0 时按「不超过该宽度」做采样解码（只降不升），用于阅读器这种
 * 原图动辄 1600~2400px 宽、而屏幕只有 1080~1440px 的场景：位图内存按 2 的幂
 * 下降，解码耗时也一起下降。传 0 表示按原尺寸解码。
 */
expect fun decodeImageBitmap(bytes: ByteArray, maxWidthPx: Int = 0): ImageBitmap?
