// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.media

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeImageBitmap(bytes: ByteArray, maxWidthPx: Int): ImageBitmap? = runCatching {
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bytes, maxWidthPx) }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}.getOrNull()

/**
 * 只读图片头算采样率（2 的幂），保证 `outWidth / sample <= maxWidthPx`。
 * `maxWidthPx <= 0` 或图片本来就不宽时返回 1（不采样）。
 */
private fun sampleSizeFor(bytes: ByteArray, maxWidthPx: Int): Int {
    if (maxWidthPx <= 0) return 1
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val width = bounds.outWidth
    if (width <= 0 || width <= maxWidthPx) return 1
    var sample = 1
    while (width / (sample * 2) >= maxWidthPx) sample *= 2
    return sample
}
