// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import app.eros.shared.settings.AppSettings

/**
 * 统一的封面模糊：所有封面入口都读同一份设置。
 * scale 用于按封面尺寸微调半径（小图不必用满设置值）。
 */
@Composable
fun Modifier.coverBlur(scale: Float = 1f): Modifier {
    val radius = AppSettings.coverBlurDp
    return if (AppSettings.blurCovers && radius > 0) {
        this.blur((radius * scale).dp)
    } else {
        this
    }
}
