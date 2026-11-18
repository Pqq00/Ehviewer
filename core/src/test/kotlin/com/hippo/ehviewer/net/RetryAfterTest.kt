// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `Retry-After` 两种写法（delta-seconds / HTTP-date）的解析。
 *
 * 用固定的 `now` 断言毫秒差，不依赖真实时钟。
 */
class RetryAfterTest {

    /** 1994-11-06T08:49:37Z，三种 HTTP-date 写法的同一时刻。 */
    private val nov6 = 784_111_777_000L

    @Test
    fun `数字形式按秒换算`() {
        assertEquals(5_000L, RetryAfter.parseMillis("5"))
        assertEquals(120_000L, RetryAfter.parseMillis(" 120 "))
        assertEquals(0L, RetryAfter.parseMillis("0"))
    }

    @Test
    fun `负数按 0 处理`() {
        assertEquals(0L, RetryAfter.parseMillis("-3"))
    }

    @Test
    fun `HTTP-date 返回距现在的剩余毫秒`() {
        assertEquals(10_000L, RetryAfter.parseMillis("Sun, 06 Nov 1994 08:49:47 GMT", now = nov6))
    }

    @Test
    fun `HTTP-date 已过期按 0`() {
        assertEquals(0L, RetryAfter.parseMillis("Sun, 06 Nov 1994 08:49:37 GMT", now = nov6 + 1))
    }

    @Test
    fun `RFC 850 两位年份不会被解释成 2094`() {
        assertEquals(10_000L, RetryAfter.parseMillis("Sunday, 06-Nov-94 08:49:37 GMT", now = nov6 - 10_000))
    }

    @Test
    fun `asctime 无时区按 GMT 解析`() {
        assertEquals(10_000L, RetryAfter.parseMillis("Sun Nov  6 08:49:37 1994", now = nov6 - 10_000))
    }

    @Test
    fun `无法解析的值返回 null`() {
        assertNull(RetryAfter.parseMillis(null))
        assertNull(RetryAfter.parseMillis("   "))
        assertNull(RetryAfter.parseMillis("soon"))
        assertNull(RetryAfter.parseMillis("Sun, 32 Nov 1994 08:49:37 GMT"))
    }
}
