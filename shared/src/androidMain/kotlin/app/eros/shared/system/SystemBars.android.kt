// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.system

import android.os.Build
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.eros.shared.settings.AndroidPlatform

actual fun setStatusBarHidden(hidden: Boolean) {
    val activity = AndroidPlatform.currentActivity ?: return
    runCatching {
        val window = activity.window ?: return
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // 从顶部/底部边缘划入时临时显示系统栏，滑走后再自动隐藏。
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 隐藏状态栏时挖孔屏的 cutout 区域也让它铺满，避免顶部留一条黑边。
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (hidden) {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }
}
