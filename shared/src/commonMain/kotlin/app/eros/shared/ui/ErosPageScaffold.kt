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
import app.eros.shared.ui.chrome.ErosGlassRecordLayer
import app.eros.shared.ui.design.erosGlassRailStartInset
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
 *  - 用 [ErosGlassRecordLayer] 把内容记录成一层，并把 `innerPadding`、`scrollBehavior`、`backdrop` 交给内容
 *
 * 采样这层的玻璃控件（顶栏 Tab、浮动工具栏、弹层）必须放在记录层之外；摆错会自采样并
 * 让 hwui prepare 递归栈溢出，规则与守卫见 [ErosGlassRecordLayer]。
 *
 * 分层契约（L0–L4）见仓库根 `UI_LAYERS.md`：本骨架对应 L1 内容记录面 + L2 顶栏，
 * 不负责 L0 窗口 chrome（侧栏/底栏）与 L3 浮层的渲染宿主。
 *
 * 页面模板只有三种，新页面按类型选：
 * - **Tabs 页**（首页/排行榜/收藏/下载/设置等）：用本骨架，底栏由 app chrome 的
 *   `ErosBottomBarHost` 统一托管，页面靠 `LocalAppBottomBarInset` 给底部留白；
 * - **详情页**：同样用本骨架，`app chrome` 依据 `BarDestination.Detail` 把底部动作栏换成
 *   收藏/下载/阅读，页面不需要额外处理；
 * - **沉浸页**（阅读器这种全屏看图/播放）：不要用本骨架，用
 *   [app.eros.shared.ui.chrome.ErosImmersiveScaffold]，它不接 chrome、顶底栏由页面自己浮在内容上。
 *
 * 用法：
 * ```
 * ErosPageScaffold(
 *     title = "首页",
 *     navigationIcon = { IconButton(...) },
 *     actions = { IconButton(...) },
 * ) { innerPadding, pageScrollBehavior, pageBackdrop ->
 *     Box(Modifier.fillMaxSize()) {
 *         // 内容不再自己挂 layerBackdrop：骨架已经记录好了
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
    /**
     * 内容是否默认避开水平安全区（横屏挖孔）。
     *
     * 需要「背景满宽 + 内容内缩」的页面（例如账号页的炫彩背景）传 `false`，自己给内容
     * 加 [app.eros.shared.ui.design.erosPageHorizontalInsets]；否则默认 `true`。
     */
    horizontalSafeInset: Boolean = true,
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
                // 玻璃侧栏展开时只把前景（标题/图标/Tab）让开，栏体背景仍满宽，
                // 这样面板右缘采到的是真实页面，不会出现位移造成的硬边界。
                contentModifier = Modifier.erosGlassRailStartInset(),
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
        // 页面内容记录成一层；采样者（玻璃 Tab/工具栏/弹层）必须在层外。
        // 规则与崩溃原因见 ErosGlassRecordLayer 的 KDoc。
        ErosGlassRecordLayer(
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize(),
            layerName = "page content",
        ) {
            // 页面内容层对页面内的玻璃弹层可见（玻璃 sheet / 对话框从这里取采样源）。
            CompositionLocalProvider(LocalPageBackdrop provides backdrop) {
                // 玻璃侧栏的让位是「内容内缩」：暴露在侧栏下方的是满宽背景与真实内容。
                // 记录层必须整宽：侧栏/顶栏的渐变带要在「任意 x」都有采样数据。
                // 水平安全区（横屏挖孔 46dp）同样只内缩内容：若挂在记录层上，记录层会从
                // 挖孔内侧才开始，顶栏渐变带在挖孔那一条没有采样，表现为左上硬边浅色矩形。
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (horizontalSafeInset) Modifier.erosPageHorizontalInsets() else Modifier)
                        .erosGlassRailStartInset(),
                ) {
                    content(innerPadding, scrollBehavior, backdrop)
                }
            }
        }
    }
}
