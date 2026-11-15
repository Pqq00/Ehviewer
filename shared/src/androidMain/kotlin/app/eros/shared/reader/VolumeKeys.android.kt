// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.reader

import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView

/**
 * Android 实现：给当前窗口挂一个按键监听。
 *
 * 只吃 VOLUME_UP / VOLUME_DOWN 的 ACTION_DOWN 并返回 true，其余事件原样放行，
 * 所以不会影响系统音量（拦截后自然不会调节音量，这也是阅读器翻页的常见做法）。
 */
actual fun Modifier.readerVolumeKeys(
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier = composed {
    if (!enabled) return@composed this
    val view = LocalView.current
    DisposableEffect(view, enabled, onPrevious, onNext) {
        val listener = View.OnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    onPrevious()
                    true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    onNext()
                    true
                }

                else -> false
            }
        }
        view.setOnKeyListener(listener)
        onDispose { view.setOnKeyListener(null) }
    }
    this
}
