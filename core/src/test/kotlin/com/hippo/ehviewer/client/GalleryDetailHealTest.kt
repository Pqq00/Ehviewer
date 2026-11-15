// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client

import com.hippo.ehviewer.net.EhSiteErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 「活画廊被误判已下架」的三道闸门：
 *  1. 404 → 只有 gdata 说它还活着且 token 变了，才用新 token 重取（[EhEngine.retryTokenFor]）；
 *  2. gdata 没能确认删除时，结论降级为"不可用"而不是"已删除"（[EhEngine.fallbackKindFor]）；
 *  3. 正文层面只有真正的不可用措辞才触发换 token，短响应不再算（[EhEngine.looksUnavailable]）。
 */
class GalleryDetailHealTest {

    @Test
    fun `gdata 说还活着且 token 变了就用新 token 重取`() {
        val result = EhEngine.GdataResult.Alive("new-token")
        assertEquals("new-token", EhEngine.retryTokenFor(result, "old-token"))
    }

    @Test
    fun `gdata 给的还是同一个 token 就不重取`() {
        assertNull(EhEngine.retryTokenFor(EhEngine.GdataResult.Alive("same"), "same"))
    }

    @Test
    fun `gdata 明确不存在 或 查询失败 时不重取`() {
        assertNull(EhEngine.retryTokenFor(EhEngine.GdataResult.NotFound, "old"))
        assertNull(EhEngine.retryTokenFor(EhEngine.GdataResult.Failed, "old"))
    }

    @Test
    fun `只有 gdata 明确不存在才判定为已删除`() {
        assertEquals(EhSiteErrorKind.GALLERY_DELETED, EhEngine.fallbackKindFor(EhEngine.GdataResult.NotFound))
    }

    @Test
    fun `gdata 查询失败时降级为不可用 不算删除`() {
        // 断网 / 被墙 / api 挂掉时，绝不能把活画廊说成"已删除"——这正是误报的主要来源。
        assertEquals(EhSiteErrorKind.GALLERY_UNAVAILABLE, EhEngine.fallbackKindFor(EhEngine.GdataResult.Failed))
        assertEquals(
            EhSiteErrorKind.GALLERY_UNAVAILABLE,
            EhEngine.fallbackKindFor(EhEngine.GdataResult.Alive("same")),
        )
    }

    @Test
    fun `不可用页与 sad panda 触发换 token`() {
        assertTrue(EhEngine.looksUnavailable("<p>This gallery is unavailable.</p>"))
        assertTrue(EhEngine.looksUnavailable("Sad Panda :("))
        assertTrue(EhEngine.looksUnavailable("sad panda"))
    }

    @Test
    fun `短但正常的详情页不再被当成不可用`() {
        // 回归：旧的 `body.length < 2000` 启发式会把 fixtures/detail_minimal.html 这种正常小页面
        // 当成"不可用"，进而误判成已下架。
        val minimal = "<html><body><div id=\"gn\">Test</div><div id=\"gdc\">200 pages</div></body></html>"
        assertTrue(minimal.length < 2000)
        assertFalse(EhEngine.looksUnavailable(minimal))
    }
}
