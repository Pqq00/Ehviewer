// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.chrome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.eros.shared.ui.ErosFloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 列表多选态的底部悬浮玻璃工具栏（下载页 / 浏览历史页共用）。
 *
 * 必须挂在页面记录层**之外**（调用点 `ErosGlassBar` 会校验），否则玻璃采样到包含
 * 自己的那一层，hwui prepare 会递归栈溢出 —— 这条规则原来以注释形式抄在两处，
 * 现在只有这一个实现。
 *
 * @param recordLayer 页面自身的记录层（[app.eros.shared.ui.ErosPageScaffold] 的 backdrop），
 *   用来给玻璃工具栏当采样源；多选工具栏在骨架之外，需要显式拿住它。
 * @param name 玻璃栏名字，自采样守卫报警时用。
 * @param enabled 是否可点（没有选中项时保持灰态）。
 * @param showLabel 是否显示「删除」文字，跟随设置里的「导航项显示」。
 */
@Composable
internal fun BoxScope.ErosSelectionToolbar(
    recordLayer: LayerBackdrop?,
    name: String,
    enabled: Boolean,
    onDelete: () -> Unit,
    showLabel: Boolean = true,
) {
    val tint = if (enabled) {
        MiuixTheme.colorScheme.error
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    }
    ErosGlassBar(name = name, backdrop = recordLayer) {
        ErosFloatingToolbar(
            backdrop = recordLayer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    // 让点击/涟漪区域与胶囊外观一致：先按胶囊形状裁剪，再覆盖整块内容 + 内边距。
                    .clip(CircleShape)
                    .clickable(enabled = enabled, onClick = onDelete)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = "删除",
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
                if (showLabel) {
                    Text(
                        text = "删除",
                        style = MiuixTheme.textStyles.footnote2,
                        color = tint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
