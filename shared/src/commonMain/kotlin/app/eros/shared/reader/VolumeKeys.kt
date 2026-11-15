// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.reader

import androidx.compose.ui.Modifier

/**
 * 让音量键在阅读器里翻页。
 *
 * 只有开启设置 `readerVolumeKeys` 时才接管，且在 [enabled] 为 false 时完全不拦截，
 * 保证退出阅读器后音量键恢复正常。
 */
expect fun Modifier.readerVolumeKeys(
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier
