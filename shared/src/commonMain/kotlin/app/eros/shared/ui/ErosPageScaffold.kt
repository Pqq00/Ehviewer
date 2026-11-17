// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.eros.shared.ui.design.erosPageHorizontalInsets
import top.yukonga.miuix.kmp.basic.Icon
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
    tabs: List<String>? = null,
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    tabVariant: ErosTabVariant = ErosTabVariant.Segmented,
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
                        onBack != null -> ErosBarIcon(onClick = onBack) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                        }
                    }
                },
                actions = actions,
                // 分段 Tab 挂在顶栏底部（官方示例的用法）：跟着顶栏一起展开/收起，
                // 不需要自己算钉位；它采样的是页面内容层，而它本身不在那层里。
                bottomContent = {
                    if (tabs != null) {
                        ErosTabRow(
                            tabs = tabs,
                            selectedIndex = selectedTab,
                            onSelect = onTabSelected,
                            backdrop = backdrop,
                            variant = tabVariant,
                            modifier = Modifier.padding(horizontal = 12.dp).padding(top = 6.dp, bottom = 12.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        // 页面内容层对页面内的玻璃弹层可见（玻璃 sheet / 对话框从这里取采样源）。
        CompositionLocalProvider(LocalPageBackdrop provides backdrop) {
            // 水平安全区只作用于内容：横屏刘海/折叠屏铰链旁边的空间不用来放东西。
            // 垂直方向不动 —— 顶栏已经自己处理 top inset，底栏各自算底部留白。
            Box(Modifier.fillMaxSize().erosPageHorizontalInsets()) {
                content(innerPadding, scrollBehavior, backdrop)
            }
        }
    }
}
