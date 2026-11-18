// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import com.hippo.ehviewer.client.EhUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EhCookieJarTest {

    @After
    fun tearDown() {
        EhCookieJar.onChange = null
        EhCookieJar.clear()
    }

    // ---- EhUrl domain helpers ----

    @Test
    fun domainMatchesItself() {
        assertTrue(EhUrl.isDomainOrSubdomainOf("e-hentai.org", "e-hentai.org"))
    }

    @Test
    fun domainMatchesSubdomain() {
        assertTrue(EhUrl.isDomainOrSubdomainOf("forums.e-hentai.org", "e-hentai.org"))
    }

    @Test
    fun domainDoesNotMatchLookalike() {
        // 关键回归：endsWith("e-hentai.org") 会把这种域名当自家站点。
        assertFalse(EhUrl.isDomainOrSubdomainOf("not-e-hentai.org", "e-hentai.org"))
        assertFalse(EhUrl.isEhSite("not-e-hentai.org"))
        assertFalse(EhUrl.isEhSite("notexhentai.org"))
    }

    @Test
    fun domainMatchIgnoresCase() {
        assertTrue(EhUrl.isDomainOrSubdomainOf("Forums.E-Hentai.ORG", "e-hentai.org"))
    }

    @Test
    fun domainMatchRejectsNull() {
        assertFalse(EhUrl.isDomainOrSubdomainOf(null, "e-hentai.org"))
        assertFalse(EhUrl.isDomainOrSubdomainOf("e-hentai.org", null))
        assertFalse(EhUrl.isEhSite(null))
    }

    @Test
    fun ehSiteCoversBothHosts() {
        assertTrue(EhUrl.isEhSite("e-hentai.org"))
        assertTrue(EhUrl.isEhSite("exhentai.org"))
        assertTrue(EhUrl.isEhSite("forums.e-hentai.org"))
        assertFalse(EhUrl.isEhSite("example.com"))
    }

    // ---- export / import ----

    @Test
    fun exportImportRoundTripKeepsHostOnly() {
        EhCookieJar.put("host_only_probe", "a", "forums.example.com", hostOnly = true)
        EhCookieJar.put("domain_probe", "b", "example.com", hostOnly = false)

        val exported = EhCookieJar.export()
        val lines = exported.lines().filter { it.isNotBlank() }

        val hostOnlyLine = lines.first { it.startsWith("host_only_probe\t") }
        val domainLine = lines.first { it.startsWith("domain_probe\t") }
        assertEquals("1", hostOnlyLine.split('\t')[7])
        assertEquals("0", domainLine.split('\t')[7])

        EhCookieJar.clear()
        EhCookieJar.import(exported)
        val reExported = EhCookieJar.export().lines().filter { it.isNotBlank() }
        assertEquals("1", reExported.first { it.startsWith("host_only_probe\t") }.split('\t')[7])
        assertEquals("0", reExported.first { it.startsWith("domain_probe\t") }.split('\t')[7])
    }

    @Test
    fun importAcceptsLegacySevenColumns() {
        val expires = System.currentTimeMillis() + 3_600_000
        // 旧格式：没有 hostOnly 列，domain 语义。
        val legacy = "legacy_probe\tv\texample.com\t/\t$expires\t0\t0"

        EhCookieJar.import(legacy)

        assertTrue(EhCookieJar.has("legacy_probe"))
        assertEquals("v", EhCookieJar.get("legacy_probe"))
        val line = EhCookieJar.export().lines().first { it.startsWith("legacy_probe\t") }
        // 重新导出时补上第 8 列，且按 domain 语义为 0。
        assertEquals("0", line.split('\t')[7])
    }

    @Test
    fun importDropsExpiredRows() {
        val past = System.currentTimeMillis() - 60_000
        val expired = "expired_probe\tv\texample.com\t/\t$past\t0\t0\t0"

        EhCookieJar.import(expired)

        assertFalse(EhCookieJar.has("expired_probe"))
        assertTrue(EhCookieJar.export().isBlank())
    }

    @Test
    fun expiredCookiesArePurgedFromExport() {
        EhCookieJar.put("alive_probe", "1", "example.com")
        // import 会整体替换 cookie 列表，所以这里单独验证"到期后自动清理"：
        // 先导入一条很快到期的 cookie，等它过期后再 export。
        val soon = System.currentTimeMillis() + 1_500
        EhCookieJar.import("short_lived\tv\texample.com\t/\t$soon\t0\t0\t0")
        assertTrue(EhCookieJar.has("short_lived"))
        assertFalse(EhCookieJar.has("alive_probe"))

        Thread.sleep(2_000)

        assertFalse(EhCookieJar.has("short_lived"))
        assertTrue(EhCookieJar.export().isBlank())
    }

    // ---- onChange ----

    @Test(timeout = 5_000)
    fun onChangeRunsOutsideTheCookieLock() {
        var callbackRan = false
        // 回调里再访问 jar：如果 onChange 在 synchronized(cookies) 内触发，这里会死锁。
        EhCookieJar.onChange = {
            callbackRan = true
            EhCookieJar.has("callback_probe")
            EhCookieJar.get("callback_probe")
        }

        EhCookieJar.put("callback_probe", "1", "example.com")

        assertTrue(callbackRan)
        assertNotNull(EhCookieJar.get("callback_probe"))
    }
}
