// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 负页（已删除 / 不可用 / 维护）绝不能进 HTTP 缓存。
 *
 * 否则一次网络抖动或一次维护，会让详情页在 `HttpCachePolicy.DETAIL`（6 小时）里一直显示"已下架"。
 */
class HttpCacheNegativePageTest {

    private lateinit var server: MockWebServer
    private lateinit var bridge: MemoryHttpCacheBridge
    private lateinit var client: OkHttpClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        bridge = MemoryHttpCacheBridge()
        client = OkHttpClient.Builder()
            .addInterceptor(HttpCacheInterceptor())
            .build()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
        HttpCacheBridgeHolder.bridge = null
    }

    private fun fetch(url: String): String {
        HttpCacheBridgeHolder.bridge = bridge
        val request = Request.Builder()
            .url(url)
            .header("X-Eh-Cache-Max-Age", HttpCachePolicy.DETAIL.maxAgeMillis.toString())
            .build()
        return client.newCall(request).execute().use { it.body?.string().orEmpty() }
    }

    private fun url() = server.url("/g/1/abc/").toString()

    @Test
    fun `不可用页不写缓存`() {
        server.enqueue(MockResponse().setBody("<p>This gallery is unavailable.</p>"))
        fetch(url())
        assertNull(bridge.get(url(), System.currentTimeMillis()))
    }

    @Test
    fun `维护页不写缓存`() {
        server.enqueue(MockResponse().setBody("<p>This gallery is pining for the fjords.</p>"))
        fetch(url())
        assertNull(bridge.get(url(), System.currentTimeMillis()))
    }

    @Test
    fun `正常详情页照常写缓存`() {
        server.enqueue(MockResponse().setBody("<html><body><div id=\"gn\">Test</div></body></html>"))
        fetch(url())
        assertNotNull(bridge.get(url(), System.currentTimeMillis()))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `第二次请求命中缓存不再打网络`() {
        server.enqueue(MockResponse().setBody("<html><body><div id=\"gn\">Test</div></body></html>"))
        fetch(url())
        fetch(url())
        assertEquals(1, server.requestCount)
    }
}
