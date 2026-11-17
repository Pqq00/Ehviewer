// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.eros.shared.ui.ErosBottomSheet
import app.eros.shared.ui.ErosDropdownPreference
import app.eros.shared.reader.ReaderFit
import app.eros.shared.reader.ReaderMode
import app.eros.shared.reader.ReaderSession
import app.eros.shared.reader.ReaderSheet
import app.eros.shared.settings.AppSettings
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 阅读器的两个弹层：跳页（点顶栏页码）/ 阅读设置（底栏按钮）。
 *
 * 必须声明在 `Scaffold` 的 content 内部，否则弹层会被顶底栏盖住。
 */
@Composable
internal fun ReaderSheets(
    session: ReaderSession,
    backdrop: LayerBackdrop? = null,
) {
    val sheet = session.sheet

    ErosBottomSheet(
        show = sheet == ReaderSheet.Jump,
        title = "跳转到页面",
        insideMargin = DpSize(16.dp, 4.dp),
        onDismissRequest = { session.closeSheet() },
        onDismissFinished = { },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            var value by remember(session.totalPages) {
                mutableFloatStateOf(session.currentPage.toFloat())
            }
            LaunchedEffect(session.currentPage) {
                value = session.currentPage.toFloat()
            }
            Text(
                text = "${value.toInt() + 1} / ${session.totalPages}",
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = {
                    session.goTo(value.toInt())
                    session.closeSheet()
                },
                valueRange = 0f..((session.totalPages - 1).coerceAtLeast(1)).toFloat(),
                steps = (session.totalPages - 1).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
        }
    }

    ErosBottomSheet(
        show = sheet == ReaderSheet.Settings,
        title = "阅读设置",
        insideMargin = DpSize(16.dp, 4.dp),
        onDismissRequest = { session.closeSheet() },
        onDismissFinished = { },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            // 外观对齐设置页：每项都是「标题 + 当前值 + 箭头」的行，点开才弹选项。
            ErosDropdownPreference(
                title = "翻页模式",
                items = ReaderMode.entries.map { it.label },
                selectedIndex = session.mode.ordinal,
                onSelectedIndexChange = {
                    session.applyMode(ReaderMode.entries[it])
                    session.keepChrome()
                },
                backdrop = backdrop,
            )

            ErosDropdownPreference(
                title = "页面适应",
                items = ReaderFit.entries.map { it.label },
                selectedIndex = ReaderFit.fromOrdinal(AppSettings.readerFit).ordinal,
                onSelectedIndexChange = { AppSettings.readerFit = it },
                backdrop = backdrop,
            )

            // miuix 0.9.5 没有玻璃版开关行，这里用「标题 + 右侧 Switch」的裸行，
            // 不再套 CheckboxPreference 自带的那层灰底，避免在玻璃弹层里割裂。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "隐藏系统状态栏",
                    style = MiuixTheme.textStyles.body1,
                )
                Switch(
                    checked = AppSettings.readerHideStatusBar,
                    onCheckedChange = { AppSettings.readerHideStatusBar = it },
                )
            }
        }
    }
}
