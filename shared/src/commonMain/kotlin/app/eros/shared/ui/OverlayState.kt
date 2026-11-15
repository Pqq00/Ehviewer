// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全局弹层计数：用于在任意底部弹层打开时隐藏底栏，避免底栏盖在弹层上方。
 */
object OverlayState {
    private var count by mutableStateOf(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val anyOpen: Boolean
        get() = count > 0

    internal fun acquire() {
        count += 1
    }

    internal fun release() {
        count = (count - 1).coerceAtLeast(0)
    }

    /** 延后释放：等 OverlayBottomSheet 的收起动画走完再让底栏回来。 */
    internal fun releaseDelayed() {
        scope.launch {
            delay(320)
            release()
        }
    }
}

/** 在弹层显示期间登记，弹层关闭（离开组合）后延迟注销。 */
@Composable
fun TrackOverlay(show: Boolean = true) {
    if (!show) return
    DisposableEffect(Unit) {
        OverlayState.acquire()
        onDispose { OverlayState.releaseDelayed() }
    }
}
