// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/**
 * desktop 侧不做采样：`org.jetbrains.skia.Image` 没有低成本的「按目标宽度解码」入口，
 * 而 desktop 只用于跑测试，内存与耗时都不是瓶颈，故忽略 [maxWidthPx]。
 */
actual fun decodeImageBitmap(bytes: ByteArray, maxWidthPx: Int): ImageBitmap? = runCatching {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()
