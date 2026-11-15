// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

import com.hippo.ehviewer.client.exception.EmptyGalleryException
import com.hippo.ehviewer.client.exception.EhException
import com.hippo.ehviewer.client.exception.GalleryUnavailableException
import com.hippo.ehviewer.client.exception.HttpStatusException
import com.hippo.ehviewer.client.exception.Image509Exception
import com.hippo.ehviewer.client.exception.ParseException
import com.hippo.ehviewer.client.exception.PiningException
import com.hippo.ehviewer.net.EhSiteErrorKind
import com.hippo.ehviewer.net.EhSiteException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EhErrorTest {

    @Test
    fun `509 异常映射为配额错误且可重试`() {
        val error = Image509Exception().toEhError()
        assertEquals(EhError.ImageLimit, error)
        assertTrue(error.isRetryable())
        assertTrue(error.userMessage().contains("配额"))
    }

    @Test
    fun `下架异常映射为 GalleryUnavailable 且不可重试`() {
        val error = GalleryUnavailableException().toEhError()
        assertEquals(EhError.GalleryUnavailable, error)
        assertFalse(error.isRetryable())
    }

    @Test
    fun `解析异常映射为 SiteChanged 并保留原始 body`() {
        val body = "x".repeat(5000)
        val error = ParseException("parse fail", body).toEhError()
        val changed = assertIs<EhError.SiteChanged>(error)
        assertEquals("parse fail", changed.detail)
        assertEquals(2048, changed.body?.length)
        assertFalse(changed.isRetryable())
        assertTrue(changed.userMessage().contains("结构"))
    }

    @Test
    fun `空画廊异常映射为 SadPanda`() {
        val error = EmptyGalleryException("empty", "<html>").toEhError()
        assertEquals(EhError.SadPanda, error)
    }

    @Test
    fun `超时异常映射为 Network 且可重试`() {
        val error = SocketTimeoutException("timeout").toEhError()
        assertIs<EhError.Network>(error)
        assertTrue(error.isRetryable())
    }

    @Test
    fun `普通 IOException 映射为 Network`() {
        assertIs<EhError.Network>(IOException("boom").toEhError())
    }

    @Test
    fun `429 映射为 RateLimited 并带上 Retry-After`() {
        val error = HttpStatusException(429, retryAfterSeconds = 30).toEhError()
        val limited = assertIs<EhError.RateLimited>(error)
        assertEquals(30L, limited.retryAfterSeconds)
        assertTrue(limited.userMessage().contains("30 秒"))
        assertTrue(limited.isRetryable())
    }

    @Test
    fun `429 没有 Retry-After 时仍可重试`() {
        val limited = assertIs<EhError.RateLimited>(HttpStatusException(429).toEhError())
        assertEquals(null, limited.retryAfterSeconds)
        assertTrue(limited.userMessage().contains("请求过快"))
    }

    @Test
    fun `403 映射为 Forbidden 且不可重试`() {
        val error = HttpStatusException(403).toEhError()
        assertEquals(EhError.Forbidden, error)
        assertFalse(error.isRetryable())
        assertTrue(error.userMessage().contains("拦截"))
    }

    @Test
    fun `5xx 映射为 Http 且可重试`() {
        val error = HttpStatusException(503).toEhError()
        assertEquals(EhError.Http(503), error)
        assertTrue(error.isRetryable())
    }

    @Test
    fun `旧式 IOException 里的 HTTP 状态码也能识别`() {
        assertEquals(EhError.RateLimited(null), IOException("HTTP 429 for https://x").toEhError())
        assertEquals(EhError.Forbidden, IOException("HTTP 403 for https://x").toEhError())
    }

    @Test
    fun `无类型信息的 EhException 按消息分类`() {
        assertEquals(EhError.NeedLogin, EhException("igneous missing").toEhError())
        assertEquals(EhError.GalleryUnavailable, EhException("This gallery is unavailable").toEhError())
        assertEquals(EhError.ImageLimit, EhException("Exceeded your image limit").toEhError())
    }

    @Test
    fun `404 删除异常映射为 GalleryDeleted 且不可重试`() {
        val error = EhSiteException(EhSiteErrorKind.GALLERY_DELETED, "画廊已下架", "https://x/g/1/t/").toEhError()
        assertEquals(EhError.GalleryDeleted, error)
        assertFalse(error.isRetryable())
        assertTrue(error.userMessage().contains("删除"))
    }

    @Test
    fun `pining 异常映射为 GalleryPining 且可重试`() {
        val error = PiningException().toEhError()
        assertEquals(EhError.GalleryPining, error)
        assertTrue(error.isRetryable())
        assertTrue(error.userMessage().contains("维护"))
    }

    @Test
    fun `消息分类区分删除 隐藏 与维护`() {
        assertEquals(EhError.GalleryDeleted, EhException("This gallery has been removed").toEhError())
        assertEquals(EhError.GalleryDeleted, EhException("Gallery not found").toEhError())
        assertEquals(
            EhError.GalleryPining,
            EhException("This gallery is pining for the fjords.").toEhError(),
        )
    }

    @Test
    fun `裸 unavailable 不再被当成已下架`() {
        // 旧逻辑匹配任意 "unavailable"，会把"需要登录"/"被隐藏"的活画廊误判成已删除。
        val error = EhException("Account unavailable, please sign in").toEhError()
        assertIs<EhError.Unknown>(error)
    }

    @Test
    fun `未知异常映射为 Unknown`() {
        val error = IllegalStateException("weird").toEhError()
        assertIs<EhError.Unknown>(error)
        assertEquals("weird", error.userMessage())
    }
}
