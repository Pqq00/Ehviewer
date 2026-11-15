// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import okhttp3.Call
import java.util.concurrent.ConcurrentHashMap

/**
 * 阻塞请求的取消登记表。
 *
 * EhEngine 是同步 API（`Call.execute()`），协程取消只会置位 Job，无法中断 socket 读；
 * 真正能让阻塞的 `execute()` 立刻抛 `IOException("Canceled")` 的只有 [Call.cancel]。
 *
 * 用法：
 * ```kotlin
 * val handle = EhCalls.newHandle()
 * job.invokeOnCompletion { EhCalls.cancel(handle) }   // 协程取消 / 超时都会走到这里
 * try { EhEngine.fillGalleryListByApi(list, handle) } finally { EhCalls.detach(handle) }
 * ```
 *
 * 一个 handle 只用一次；取消后再次 [attach] 的请求会被立刻掐掉（先取消后发起的竞态兜底）。
 */
object EhCalls {

    /** 一次性取消令牌。 */
    class Handle internal constructor() {
        @Volatile
        internal var cancelled: Boolean = false
    }

    private val active = ConcurrentHashMap<Handle, Call>()

    fun newHandle(): Handle = Handle()

    /**
     * 登记一个刚发起的请求。
     *
     * 如果这个 handle 在此之前已经被 [cancel]（例如超时先到、请求后发），这里立刻取消它，
     * 不让它白占一次 25s 的 callTimeout。
     */
    fun attach(handle: Handle, call: Call) {
        active[handle] = call
        if (handle.cancelled) {
            active.remove(handle)
            call.cancel()
        }
    }

    /** 请求结束（成功/失败/取消）后撤销登记，避免登记表泄漏。 */
    fun detach(handle: Handle) {
        active.remove(handle)
    }

    /** 取消该 handle 名下的在途请求。可重复调用。 */
    fun cancel(handle: Handle) {
        handle.cancelled = true
        active.remove(handle)?.cancel()
    }

    /** 当前登记的在途请求数（测试/排查用）。 */
    fun activeCount(): Int = active.size
}
