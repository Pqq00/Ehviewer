// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CookieParserTest {

    @Test
    fun `解析带分号的完整 cookie 串`() {
        val parsed = parseEhCookies(
            "sk=abcdef; ipb_member_id=123456; ipb_pass_hash=a1b2c3d4e5; igneous=0123456789abcdef0123",
        )
        assertEquals("123456", parsed.memberId)
        assertEquals("a1b2c3d4e5", parsed.passHash)
        assertEquals("0123456789abcdef0123", parsed.igneous)
        assertTrue(parsed.any)
        assertTrue(parsed.missing.isEmpty())
    }

    @Test
    fun `去掉 Cookie 前缀并忽略大小写与顺序`() {
        val parsed = parseEhCookies(
            "Cookie: IPB_PASS_HASH=deadbeef00; IPB_MEMBER_ID=42",
        )
        assertEquals("42", parsed.memberId)
        assertEquals("deadbeef00", parsed.passHash)
        assertEquals(null, parsed.igneous)
    }

    @Test
    fun `换行分隔也能解析`() {
        val parsed = parseEhCookies("ipb_member_id=7\nipb_pass_hash=0011223344\nigneous=ffeeddccbbaa99887766")
        assertEquals("7", parsed.memberId)
        assertEquals("0011223344", parsed.passHash)
        assertEquals("ffeeddccbbaa99887766", parsed.igneous)
    }

    @Test
    fun `只有值没有名字时按形态猜测`() {
        val parsed = parseEhCookies("123456\n0011223344\nffeeddccbbaa99887766")
        assertEquals("123456", parsed.memberId)
        assertEquals("0011223344", parsed.passHash)
        assertEquals("ffeeddccbbaa99887766", parsed.igneous)
    }

    @Test
    fun `带引号的值会去掉引号`() {
        val parsed = parseEhCookies("ipb_member_id=\"123\"; ipb_pass_hash='aabbccddee'")
        assertEquals("123", parsed.memberId)
        assertEquals("aabbccddee", parsed.passHash)
    }

    @Test
    fun `缺少字段时 missing 会列出来`() {
        val parsed = parseEhCookies("ipb_member_id=1")
        assertEquals(listOf("ipb_pass_hash"), parsed.missing)
        assertTrue(parsed.any)
    }

    @Test
    fun `空文本和无关文本不产生结果`() {
        assertFalse(parseEhCookies("").any)
        assertFalse(parseEhCookies("   ").any)
        assertFalse(parseEhCookies("hello world").any)
        assertFalse(parseEhCookies("sk=abcdef; hpHm=xyz").any)
    }
}
