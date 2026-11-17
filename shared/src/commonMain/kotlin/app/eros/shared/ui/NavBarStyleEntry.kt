// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode

/** 导航栏容器样式。 */
enum class NavBarStyleEntry(val label: String) {
    FIXED("固定"),
    FLOATING("悬浮"),
    IOS("iOS"),
    GLASS("玻璃"),
}

/** 导航项显示模式。 */
enum class NavBarModeEntry(val label: String, val mode: NavigationBarDisplayMode) {
    ICON_AND_TEXT("图标+文字", NavigationBarDisplayMode.IconAndText),
    ICON_ONLY("仅图标", NavigationBarDisplayMode.IconOnly),
    ICON_WITH_SELECTED_LABEL("选中显示文字", NavigationBarDisplayMode.IconWithSelectedLabel),
}
