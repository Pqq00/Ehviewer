// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [RetryInterceptor] 的归属语义：只重试 GET 的 5xx / 429，`Retry-After`
 * 支持 HTTP-date，POST 永不重放。
 *
 * 退避有抖动，所以只断言「请求了几次 + 最终拿到哪个响应」，不断言耗时。
 */
class RetryInterceptorTest {

    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun client(maxRetries: Int = 2): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(RetryInterceptor(maxRetries = maxRetries))
        .build()

    private fun get(path: String = "/page"): Request =
        Request.Builder().url(server.url(path)).build()

    private fun post(path: String = "/page"): Request =
        Request.Builder()
            .url(server.url(path))
            .post("a=1".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

    private fun status(response: okhttp3.Response): Int = response.use { it.code }

    @Test
    fun `5xx 后重试并返回成功`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val code = status(client().newCall(get()).execute())

        assertEquals(200, code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `重试次数用尽后返回最后一次响应`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(504))

        val code = status(client(maxRetries = 1).newCall(get()).execute())

        assertEquals(504, code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `429 的 Retry-After 支持 HTTP-date`() {
        val past = DateTimeFormatter.RFC_1123_DATE_TIME
            .format(ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(60))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", past))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val code = status(client().newCall(get()).execute())

        assertEquals(200, code)
        assertEquals(2, server.requestCount, "HTTP-date 解析成功；过期日期应立即重试而不是被当成「没给提示」")
    }

    @Test
    fun `429 的数字 Retry-After 生效`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val code = status(client().newCall(get()).execute())

        assertEquals(200, code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `POST 不重试`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val code = status(client().newCall(post()).execute())

        assertEquals(503, code, "POST 重放可能有副作用，必须原样返回")
        assertEquals(1, server.requestCount)
    }
}
