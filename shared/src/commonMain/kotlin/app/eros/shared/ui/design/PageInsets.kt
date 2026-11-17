// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.design

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 只消费水平方向的安全区（横屏刘海、折叠屏铰链、曲面屏侧边）。
 *
 * 对应官方 example 的 `Modifier.appPageHorizontalInsets()`（`utils/PageUtils.kt:123-127`）：
 * 页面内容与底部胶囊要避开侧边的摄像头/铰链，但不动垂直方向 —— 垂直留白由顶栏、底栏
 * 各自的安全区计算负责（[app.eros.shared.ui.ErosTopBar] / [app.eros.shared.ui.ErosBottomNavBar]）。
 *
 * 普通手机上水平安全区为 0，本修饰符没有任何效果。
 */
@Composable
fun Modifier.erosPageHorizontalInsets(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
