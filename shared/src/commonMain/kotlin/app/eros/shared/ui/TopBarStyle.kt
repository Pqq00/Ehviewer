// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import app.eros.shared.settings.AppSettings

/**
 * 顶栏模糊样式。对齐官方示例的 `BlurStyleOptions = ["Gaussian", "Progressive"]`：
 *
 *  - Gaussian：整块高斯模糊（`textureBlur`，blurRadius 25）
 *  - Progressive：顶部最强、向下渐隐到清晰（`progressiveTextureBlur`，
 *    curve 2.2、blurRadius 10、混合色更淡）
 *
 * 「是否启用」由 `AppSettings.barBlurEnabled` 单独控制，与官方 `enableBlur` 一致。
 */
enum class BarBlurStyle(val label: String) {
    GAUSSIAN("高斯模糊"),
    PROGRESSIVE("渐进模糊"),
    ;

    companion object {
        fun fromIndex(index: Int): BarBlurStyle = entries.getOrElse(index) { GAUSSIAN }
    }
}

fun currentBarBlurStyle(): BarBlurStyle = BarBlurStyle.fromIndex(AppSettings.barBlurStyle)
