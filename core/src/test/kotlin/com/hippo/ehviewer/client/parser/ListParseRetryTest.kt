// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListParseRetryTest {

    @Test
    fun `各种列表视图标记都能识别`() {
        assertTrue(ListParseRetry.looksLikeListPage("<table class=\"itg gltc\">"))
        assertTrue(ListParseRetry.looksLikeListPage("<table class=\"itg gltm\">"))
        assertTrue(ListParseRetry.looksLikeListPage("<table class=\"itg glte\">"))
        assertTrue(ListParseRetry.looksLikeListPage("<div class=\"searchnav\">"))
    }

    @Test
    fun `非列表页不误判`() {
        assertFalse(ListParseRetry.looksLikeListPage(""))
        assertFalse(ListParseRetry.looksLikeListPage("<html><body>Sad Panda</body></html>"))
    }

    @Test
    fun `解析出 0 条且是列表页时需要重试`() {
        val body = "<table class=\"itg gltc\">"
        assertTrue(ListParseRetry.shouldRetry(body, parsedCount = 0))
        assertFalse(ListParseRetry.shouldRetry(body, parsedCount = 3))
        assertFalse(ListParseRetry.shouldRetry("<html>error</html>", parsedCount = 0))
    }

    @Test
    fun `拼参数区分是否已有查询串`() {
        assertEquals(
            "https://e-hentai.org/?inline_set=dm_l",
            ListParseRetry.withInlineSet("https://e-hentai.org/"),
        )
        assertEquals(
            "https://e-hentai.org/?f_search=abc&inline_set=dm_l",
            ListParseRetry.withInlineSet("https://e-hentai.org/?f_search=abc"),
        )
    }

    @Test
    fun `已有同名参数时不重复追加`() {
        val url = "https://e-hentai.org/?inline_set=dm_l"
        assertEquals(url, ListParseRetry.withInlineSet(url))
    }
}
