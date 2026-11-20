// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.chrome

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 搜索模式是否展开（L3 浮层）。
 *
 * 窗口级顶栏据此做与官方 `GlassPage` 一致的收放动画：搜索打开时顶栏缩小、模糊并淡出，
 * 而不是像普通页面切换那样整体瞬移。
 */
internal val LocalSearchModeExpanded = staticCompositionLocalOf { false }

/**
 * 页面整体（chrome 内容列）的淡出量：1 = 正常，0 = 搜索浮层已完全接管。
 * 由 `App` 的 `rememberGlassSearchBarAlpha(searchExpanded)` 驱动，顶栏动画与页面淡出同步。
 */
internal val LocalSearchBarAlpha = staticCompositionLocalOf<State<Float>> { mutableStateOf(1f) }
