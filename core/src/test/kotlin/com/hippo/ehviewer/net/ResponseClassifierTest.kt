// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ResponseClassifier] 的标记表。这里的字符串都摘自站点真实页面，
 * 改动时优先相信这里的测试，而不是记忆里的文案。
 */
class ResponseClassifierTest {

    private fun classify(body: String, code: Int = 200, contentType: String? = "text/html; charset=utf-8") =
        ResponseClassifier.classify(code, body.toByteArray(), contentType)

    @Test
    fun `普通画廊页面是 NORMAL`() {
        assertEquals(
            ResponseClass.NORMAL,
            classify("<html><body><div id=\"gn\">Some Gallery</div></body></html>"),
        )
    }

    @Test
    fun `已删除画廊是 REMOVED`() {
        assertEquals(ResponseClass.REMOVED, classify("<p>This gallery has been removed.</p>"))
        assertEquals(ResponseClass.REMOVED, classify("<p>This gallery is unavailable.</p>"))
        assertEquals(ResponseClass.REMOVED, classify("<p>Gallery not found.</p>"))
    }

    @Test
    fun `维护页是 MAINTENANCE`() {
        assertEquals(ResponseClass.MAINTENANCE, classify("<p>This gallery is pining for the fjords.</p>"))
        assertEquals(ResponseClass.MAINTENANCE, classify("<p>We are currently performing maintenance.</p>"))
    }

    @Test
    fun `sad panda 是 SAD_PANDA`() {
        assertEquals(ResponseClass.SAD_PANDA, classify("<html><h1>Sad Panda</h1></html>"))
        assertEquals(ResponseClass.SAD_PANDA, classify("<html><h1>Sad Panda</h1></html>"))
    }

    @Test
    fun `登录提示是 LOGIN_REQUIRED`() {
        assertEquals(ResponseClass.LOGIN_REQUIRED, classify("<p>You are not logged in.</p>"))
        assertEquals(
            ResponseClass.LOGIN_REQUIRED,
            classify("<p>This page requires you to be logged in.</p>"),
        )
    }

    @Test
    fun `IP 限流是 BANNED`() {
        assertEquals(
            ResponseClass.BANNED,
            classify("<p>Your IP address has been temporarily banned for excessive pageloads.</p>"),
        )
    }

    @Test
    fun `Cloudflare 是 CLOUDFLARE`() {
        assertEquals(
            ResponseClass.CLOUDFLARE,
            classify("<html><title>Just a moment...</title></html>", code = 403),
        )
    }

    @Test
    fun `5xx 是 SERVER_ERROR`() {
        assertEquals(ResponseClass.SERVER_ERROR, classify("oops", code = 500))
        assertEquals(ResponseClass.SERVER_ERROR, classify("oops", code = 502))
    }

    @Test
    fun `二进制内容不做标记扫描`() {
        // PNG 头 + 一段恰好包含标记文字的字节：类型不是文本时应判 NORMAL（可缓存）。
        val bytes = "This gallery is unavailable".toByteArray()
        assertEquals(ResponseClass.NORMAL, ResponseClassifier.classify(200, bytes, "image/jpeg"))
    }

    @Test
    fun `空响应是 UNKNOWN 且不可缓存`() {
        val cls = ResponseClassifier.classify(200, ByteArray(0), "text/html")
        assertEquals(ResponseClass.UNKNOWN, cls)
        assertFalse(ResponseClassifier.isCacheable(cls))
    }

    @Test
    fun `只有 NORMAL 可缓存`() {
        assertTrue(ResponseClassifier.isCacheable(ResponseClass.NORMAL))
        ResponseClass.entries.filter { it != ResponseClass.NORMAL }.forEach {
            assertFalse(ResponseClassifier.isCacheable(it), "$it 不应落缓存")
        }
    }
}
