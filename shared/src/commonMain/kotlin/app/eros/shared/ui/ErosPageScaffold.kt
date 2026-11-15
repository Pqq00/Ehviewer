// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

/**
 * 页面骨架：统一「模糊顶栏 + 页面 backdrop」的样板代码。
 *
 * 各页面仍然自己组织内容容器（Box/Grid/Pager），骨架只负责：
 *  - 创建 `MiuixScrollBehavior` 与 `rememberBarBackdrop()`
 *  - 渲染 `ErosTopBar`（标题 / 左侧按钮 / 右侧操作）
 *  - 把 `innerPadding`、`scrollBehavior`、`backdrop` 交给内容
 *
 * 内容层需要自己挂 `Modifier.layerBackdrop(backdrop)`（各页 Box 里那行不变），
 * 这样顶栏模糊才能采到页面内容。
 *
 * 用法：
 * ```
 * ErosPageScaffold(
 *     title = "首页",
 *     navigationIcon = { IconButton(...) },
 *     actions = { IconButton(...) },
 * ) { innerPadding, pageScrollBehavior, pageBackdrop ->
 *     Box(Modifier.fillMaxSize().then(if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier)) {
 *         ...
 *     }
 * }
 * ```
 */
@Composable
fun ErosPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets(0),
    snackbarHost: @Composable () -> Unit = {},
    floatingToolbar: @Composable () -> Unit = {},
    floatingToolbarPosition: ToolbarPosition = ToolbarPosition.BottomCenter,
    content: @Composable (
        innerPadding: PaddingValues,
        scrollBehavior: ScrollBehavior,
        backdrop: LayerBackdrop?,
    ) -> Unit,
) {
    val backdrop = rememberBarBackdrop()
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = contentWindowInsets,
        snackbarHost = snackbarHost,
        floatingToolbar = floatingToolbar,
        floatingToolbarPosition = floatingToolbarPosition,
        topBar = {
            ErosTopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                navigationIcon = {
                    when {
                        navigationIcon != null -> navigationIcon()
                        onBack != null -> IconButton(onClick = onBack) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                        }
                    }
                },
                actions = actions,
            )
        },
    ) { innerPadding ->
        content(innerPadding, scrollBehavior, backdrop)
    }
}
