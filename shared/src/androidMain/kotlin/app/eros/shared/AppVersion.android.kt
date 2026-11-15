// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package app.eros.shared

import android.content.pm.PackageInfo
import android.os.Build
import app.eros.shared.settings.AndroidPlatform

/**
 * 从已安装的 APK 读取版本信息，保证「关于」显示的版本与 Gradle 配置一致：
 * debug 变体带 `-dev` 后缀，release 变体是 `.gallery` 包，各自读到各自的值。
 */
actual fun platformAppVersionName(): String = runCatching {
    packageInfo().versionName
}.getOrNull().orEmpty()

actual fun platformAppVersionCode(): Long = runCatching {
    val info = packageInfo()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        info.versionCode.toLong()
    }
}.getOrDefault(0L)

private fun packageInfo(): PackageInfo {
    val ctx = AndroidPlatform.appContext
    return ctx.packageManager.getPackageInfo(ctx.packageName, 0)
}
