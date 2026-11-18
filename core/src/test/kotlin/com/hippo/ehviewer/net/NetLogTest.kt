// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [NetLog] 的分类标注：排查「页面看起来是空的」时要能一眼看出是
 * 登录失效、sad panda 还是普通内容。
 */
class NetLogTest {

    @BeforeTest
    fun setUp() {
        NetLog.clear()
        NetLog.enabled = true
    }

    @AfterTest
    fun tearDown() {
        NetLog.enabled = false
        NetLog.clear()
    }

    @Test
    fun `分类标在 URL 与错误之间`() {
        NetLog.log("GET", "https://e-hentai.org/g/1/abc/", 200, 42, 1234, null, "LOGIN_REQUIRED")

        val entry = NetLog.snapshot().single()
        val line = NetLog.formatEntry(entry)

        assertTrue(line.contains("LOGIN_REQUIRED"), "应带上分类：$line")
        assertTrue(line.indexOf("LOGIN_REQUIRED") > line.indexOf("https://"), "分类应在 URL 之后：$line")
    }

    @Test
    fun `没有分类时不输出方括号`() {
        NetLog.log("GET", "https://e-hentai.org/g/1/abc/", 200, 42, 1234, null)

        val line = NetLog.formatEntry(NetLog.snapshot().single())

        assertFalse(line.contains("["), "未分类不应出现分类标记：$line")
    }

    @Test
    fun `错误信息仍排在分类之后`() {
        NetLog.log("GET", "https://e-hentai.org/g/1/abc/", 200, 42, 1234, "GALLERY_DELETED", "REMOVED")

        val line = NetLog.formatEntry(NetLog.snapshot().single())

        assertTrue(line.contains("[REMOVED]"), line)
        assertTrue(line.indexOf("[REMOVED]") < line.indexOf("GALLERY_DELETED"), line)
    }

    @Test
    fun `未启用时不记录`() {
        NetLog.enabled = false
        NetLog.log("GET", "https://e-hentai.org/g/1/abc/", 200, 42, 1234, null, "NORMAL")

        assertTrue(NetLog.snapshot().isEmpty())
    }
}
