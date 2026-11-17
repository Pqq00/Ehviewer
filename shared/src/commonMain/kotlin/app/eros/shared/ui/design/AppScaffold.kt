// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.design

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.eros.shared.ui.ErosPageScaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 设计层页面骨架。
 *
 * 各页面只依赖 `AppScaffold`，不直接依赖 miuix `Scaffold` / `ErosTopBar`。
 * 目前内部委托给 [ErosPageScaffold]，把「模糊顶栏 + backdrop + scrollBehavior」的
 * 接线固定在一处，页面拿到的依旧是 `(innerPadding, scrollBehavior, backdrop)`。
 *
 * 用法：
 * ```
 * AppScaffold(title = "排行榜") { innerPadding, scrollBehavior, backdrop ->
 *     Box(Modifier.fillMaxSize().then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)) { ... }
 * }
 * ```
 */
@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    tabs: List<String>? = null,
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    content: @Composable (
        innerPadding: PaddingValues,
        scrollBehavior: ScrollBehavior,
        backdrop: LayerBackdrop?,
    ) -> Unit,
) {
    ErosPageScaffold(
        title = title,
        modifier = modifier,
        onBack = onBack,
        navigationIcon = navigationIcon,
        actions = actions,
        snackbarHost = snackbarHost,
        tabs = tabs,
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        content = content,
    )
}
