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
 * 非内容页（已删除 / 不可用 / 维护 / 登录提示 / Cloudflare）绝不能进 HTTP 缓存。
 *
 * 否则一次网络抖动、一次 cookie 过期或一次维护，会让详情页在 `HttpCachePolicy.DETAIL`
 * （6 小时）里一直显示"已下架/请登录"，只能靠清缓存恢复。
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
        com.hippo.ehviewer.Settings.isExHentai = false
    }

    private fun url() = server.url("/g/1/abc/").toString()

    /** 缓存 key 带站点/登录维度，测试要按同样的 key 去查。 */
    private fun key(u: String = url()) = HttpCacheKeys.scoped(u)

    private fun fetch(
        u: String = url(),
        contentType: String = "text/html; charset=utf-8",
        code: Int = 200,
    ): okhttp3.Response {
        HttpCacheBridgeHolder.bridge = bridge
        val request = Request.Builder()
            .url(u)
            .header("X-Eh-Cache-Max-Age", HttpCachePolicy.DETAIL.maxAgeMillis.toString())
            .build()
        return client.newCall(request).execute().use { response ->
            // 读掉 body，保证拦截器里的分类逻辑走完
            response.body?.string()
            response.newBuilder()
                .body(okhttp3.ResponseBody.create(null, ByteArray(0)))
                .build()
        }
    }

    private fun enqueue(body: String, code: Int = 200, contentType: String = "text/html; charset=utf-8") {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", contentType))
    }

    @Test
    fun `不可用页不写缓存`() {
        enqueue("<p>This gallery is unavailable.</p>")
        fetch()
        assertNull(bridge.get(key(), System.currentTimeMillis()))
    }

    @Test
    fun `维护页不写缓存`() {
        enqueue("<p>This gallery is pining for the fjords.</p>")
        fetch()
        assertNull(bridge.get(key(), System.currentTimeMillis()))
    }

    @Test
    fun `登录提示页不写缓存`() {
        enqueue("<p>You are not logged in.</p>")
        fetch()
        assertNull(bridge.get(key(), System.currentTimeMillis()))
    }

    @Test
    fun `sad panda 不写缓存`() {
        enqueue("<html><body><h1>Sad Panda</h1></body></html>")
        fetch()
        assertNull(bridge.get(key(), System.currentTimeMillis()))
    }

    @Test
    fun `IP 限流页不写缓存`() {
        enqueue("<p>Your IP address has been temporarily banned for excessive pageloads.</p>")
        fetch()
        assertNull(bridge.get(key(), System.currentTimeMillis()))
    }

    @Test
    fun `Cloudflare 挑战页不写缓存`() {
        enqueue("<html><title>Just a moment...</title></html>", code = 403)
        fetch()
        assertNull(bridge.get(key(), System.currentTimeMillis()))
    }

    @Test
    fun `正常详情页照常写缓存`() {
        enqueue("<html><body><div id=\"gn\">Test</div></body></html>")
        fetch()
        assertNotNull(bridge.get(key(), System.currentTimeMillis()))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `第二次请求命中缓存不再打网络`() {
        enqueue("<html><body><div id=\"gn\">Test</div></body></html>")
        fetch()
        fetch()
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `命中缓存时标注为 NORMAL`() {
        enqueue("<html><body><div id=\"gn\">Test</div></body></html>")
        val first = fetch()
        assertEquals(ResponseClass.NORMAL.name, first.header(ResponseClassifier.HEADER))
        val second = fetch()
        assertEquals("HIT", second.header("X-Eh-HttpCache"))
        assertEquals(ResponseClass.NORMAL.name, second.header(ResponseClassifier.HEADER))
    }

    @Test
    fun `网络响应标注分类`() {
        enqueue("<p>This gallery has been removed.</p>")
        val response = fetch()
        assertEquals(ResponseClass.REMOVED.name, response.header(ResponseClassifier.HEADER))
    }

    @Test
    fun `切换站点后不复用另一种站点的缓存`() {
        enqueue("<html><body><div id=\"gn\">E-hentai</div></body></html>")
        fetch()
        assertEquals(1, server.requestCount)

        // 同一个 URL，但站点维度从表站切到里站：必须重新走网络。
        com.hippo.ehviewer.Settings.isExHentai = true
        enqueue("<html><body><div id=\"gn\">ExHentai</div></body></html>")
        fetch()
        assertEquals(2, server.requestCount)
    }
}
