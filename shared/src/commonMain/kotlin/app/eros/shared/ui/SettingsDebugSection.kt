// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import app.eros.shared.settings.AppSettings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.eros.shared.diag.setNetLogEnabled
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** 设置页「调试」一节，只在 debug 包里出现。 */
@Composable
internal fun SettingsDebugSection(onOpenNetLog: () -> Unit) {
    var netLogOn by remember { mutableStateOf(AppSettings.netLogEnabled) }
    SwitchPreference(
        title = "显示 FPS",
        checked = AppSettings.showFps,
        onCheckedChange = { AppSettings.showFps = it },
    )
    SwitchPreference(
        title = "网络日志",
        checked = netLogOn,
        onCheckedChange = {
            netLogOn = it
            AppSettings.netLogEnabled = it
            setNetLogEnabled(it)
        },
    )
    AnimatedVisibility(visible = netLogOn) {
        ArrowPreference(
            title = "查看日志",
            onClick = onOpenNetLog,
        )
    }
    // 只用于验证崩溃日志这条链路：点一下会立刻闪退，下次启动能看到崩溃提示。
    ArrowPreference(
        title = "模拟崩溃",
        onClick = { throw IllegalStateException("诊断测试：模拟崩溃") },
    )
}
