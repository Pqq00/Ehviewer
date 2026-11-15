// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.reader

import androidx.compose.ui.Modifier

/** 桌面端没有音量键，直接忽略。 */
actual fun Modifier.readerVolumeKeys(
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier = this
