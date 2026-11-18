// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 沉浸式页面模板（`UI_LAYERS.md` 的第三种页面模板）。
 *
 * 与 [app.eros.shared.ui.ErosPageScaffold] 的分工：
 * - 不接 app chrome：没有 `ErosTopBar`，`LocalAppBottomBarInset` 在这里恒为 0，页面不需要让位；
 * - 顶/底栏由页面自己用 `AnimatedVisibility` 浮在内容之上，内容空间的尺寸不随栏体显隐变化；
 * - 自带 L1 记录层（`layerName = "immersive page"`），页面里的玻璃栏/浮层照常采样。
 *
 * 阅读器是当前唯一使用者；新增全屏看图/播放类页面走这里，不要再手写 `Scaffold`。
 */
@Composable
internal fun ErosImmersiveScaffold(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    background: Color = Color.Black,
    recordModifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        // 容器透出页面底色；顶底栏不占 Scaffold 槽位（浮层），内容始终满屏。
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
    ) { _ ->
        Box(Modifier.fillMaxSize().background(background)) {
            ErosGlassRecordLayer(
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize().then(recordModifier),
                layerName = "immersive page",
            ) {
                content()
            }
            Box(Modifier.align(Alignment.TopCenter)) { topBar() }
            Box(Modifier.align(Alignment.BottomCenter)) { bottomBar() }
        }
    }
}
