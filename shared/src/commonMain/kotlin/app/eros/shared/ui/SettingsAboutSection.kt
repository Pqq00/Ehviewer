// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.runtime.Composable
import app.eros.shared.BuildInfo
import app.eros.shared.diag.exportDiagnostics
import app.eros.shared.system.showSystemToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.preference.ArrowPreference

/** 设置页「关于」一节：日志导出与版本号（release 也需要，所以不放调试组）。 */
@Composable
internal fun SettingsAboutSection(scope: CoroutineScope) {
    ArrowPreference(
        title = "导出日志",
        onClick = {
            scope.launch { showSystemToast(exportDiagnostics(share = false)) }
        },
    )
    ArrowPreference(
        title = "分享日志",
        onClick = {
            scope.launch { showSystemToast(exportDiagnostics(share = true)) }
        },
    )
    BasicComponent(
        title = "版本",
        summary = "${BuildInfo.version} (${BuildInfo.versionCode})",
    )
}
