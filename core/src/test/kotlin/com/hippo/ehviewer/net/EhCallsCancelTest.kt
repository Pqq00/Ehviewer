// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import com.hippo.ehviewer.client.EhEngine
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证「取消 → Call.cancel() → 阻塞请求立刻退出」这条链路。
 *
 * core 模块没有协程依赖，所以直接在后台线程跑 EhEngine 的阻塞请求，
 * 主线程按 handle 取消，量它多久返回；协程侧只是把 cancel 挂到
 * `Job.invokeOnCompletion` 上，语义一致。
 */
class EhCallsCancelTest {

    /** 后台线程发起一次阻塞请求，返回「异常 or null」。 */
    private fun startBlocking(url: String, handle: EhCalls.Handle): CompletableFuture<Throwable?> {
        val done = CompletableFuture<Throwable?>()
        Thread {
            try {
                EhEngine.request(url, null, true, handle)
                done.complete(null)
            } catch (t: Throwable) {
                done.complete(t)
            }
        }.apply { isDaemon = true }.start()
        return done
    }

    /**
     * 服务端迟迟不回响应头（10s 太久：MockWebServer.shutdown 只等 5s 队列，会掩盖真实断言）。
     * 3s 足够覆盖「取消发生在执行中」这段窗口，又能在测试结束时正常收尾。
     */
    private fun slowServer(): MockWebServer = MockWebServer().apply {
        enqueue(MockResponse().setBody("late").setHeadersDelay(3, TimeUnit.SECONDS))
        start()
    }

    @Test
    fun `cancel interrupts a blocked request quickly`() {
        val server = slowServer()
        try {
            val handle = EhCalls.newHandle()
            val done = startBlocking(server.url("/slow").toString(), handle)
            Thread.sleep(300) // 等请求真正发出去并登记
            assertTrue(EhCalls.activeCount() >= 1, "在途请求应当已登记到 EhCalls")

            val startedAt = System.nanoTime()
            EhCalls.cancel(handle)
            val failure = done.get(3, TimeUnit.SECONDS)
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

            assertTrue(failure != null, "被取消的请求应当抛异常，而不是正常返回")
            assertTrue(elapsedMs < 1200, "取消后应立即返回，实际等了 $elapsedMs ms")
            assertEquals(0, EhCalls.activeCount(), "登记表应当已清空")
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `cancel before the call starts still cancels it`() {
        val server = slowServer()
        try {
            val handle = EhCalls.newHandle()
            EhCalls.cancel(handle) // 超时比请求先到
            val done = startBlocking(server.url("/late").toString(), handle)
            val failure = done.get(3, TimeUnit.SECONDS)
            assertTrue(failure != null, "先取消再发起时请求也应被掐掉")
            assertEquals(0, EhCalls.activeCount(), "登记表应当已清空")
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `normal request leaves no registration`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        try {
            val handle = EhCalls.newHandle()
            val body = EhEngine.request(server.url("/ok").toString(), null, true, handle)
            assertEquals("ok", body)
            assertEquals(0, EhCalls.activeCount(), "正常结束后不应残留登记")
        } finally {
            server.shutdown()
        }
    }
}
