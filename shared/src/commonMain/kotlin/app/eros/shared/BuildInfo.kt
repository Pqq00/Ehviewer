// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

/**
 * 应用版本信息。
 *
 * 版本号不写死：Android 侧在运行时读取安装包（PackageManager）里的
 * versionName / versionCode，因此 debug 的 `-dev` 后缀、release 的
 * `.gallery` 包都会自动跟随 `android/build.gradle.kts` 的配置。
 */
object BuildInfo {
    /** 版本号（含渠道后缀，例如 `1.0.1-dev`）。 */
    val version: String get() = platformAppVersionName()

    /** 构建号（对应 versionCode）。 */
    val versionCode: Long get() = platformAppVersionCode()

    /** 应用显示名。改这里时同步改 android/src/main/AndroidManifest.xml 的 android:label。 */
    const val name: String = "Ehviewer"

    /** 技术栈说明，用于需要更完整描述的场合。 */
    const val stack: String = "Miuix + Compose Multiplatform"
}

/** 安装包的 versionName；无包信息的平台返回占位值。 */
expect fun platformAppVersionName(): String

/** 安装包的 versionCode；无包信息的平台返回 0。 */
expect fun platformAppVersionCode(): Long
