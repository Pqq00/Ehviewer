// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.chrome

import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 窗口级记录层（`UI_LAYERS.md` 里的 L0 chrome 记录的整页层），由 [ErosAppChrome] 下发。
 *
 * 用途区分：
 * - 页面内浮层（dialog / bottom sheet 属于某页内容）应采样
 *   [app.eros.shared.ui.LocalPageBackdrop]（L1 内容层），这样只模糊页面内容；
 * - 窗口级浮层（不属于页面、要盖在侧栏与底栏之上）用这个（L1 整页层）。
 *
 * 不要在页面里把它当默认值用；显式选择层级本身就是契约的一部分。
 */
internal val LocalChromeBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
