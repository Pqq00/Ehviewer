// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsTest {

    @Test
    fun `默认值符合预期`() {
        val s = Settings()
        assertEquals(Settings.CURRENT_SCHEMA_VERSION, s.schemaVersion)
        assertEquals(0, s.themeMode)
        assertFalse(s.blurCovers)
        assertEquals(0, s.coverAspect)
        assertEquals(2, s.titleLines)
        assertEquals(1, s.navBarStyle)
        assertEquals(1, s.barBlurStyle)
        assertTrue(s.barBlurEnabled)
        assertFalse(s.predictiveBack)
        assertFalse(s.swipeBack)
        assertTrue(s.detailPrefetch)
        assertEquals(3, s.readerPreloadPages)
        assertEquals(500, s.pageCacheLimitMb)
        assertEquals(7, s.pageListTtlDays)
        assertEquals("127.0.0.1", s.proxyHost)
        assertEquals(7890, s.proxyPort)
    }

    @Test
    fun `JSON 往返保持所有字段`() {
        val original = Settings(
            themeMode = 2,
            blurCovers = false,
            coverBlurDp = 12,
            titleLines = 3,
            readerPreloadPages = 10,
            pageCacheLimitMb = 1200,
            exHentai = true,
            proxyOn = true,
            proxyHost = "10.0.0.2",
            proxyPort = 1080,
            filterRulesJson = "[{\"text\":\"test\"}]",
        )
        val decoded = SettingsCodec.decode(SettingsCodec.encode(original))
        assertEquals(original.copy(schemaVersion = Settings.CURRENT_SCHEMA_VERSION), decoded)
    }

    @Test
    fun `损坏的 JSON 解码为 null 而不是崩溃`() {
        assertNull(SettingsCodec.decode("{not json"))
        assertNull(SettingsCodec.decode(""))
        assertNull(SettingsCodec.decode(null))
    }

    @Test
    fun `未知字段被忽略（向前兼容旧版本）`() {
        val withExtra = """{"schemaVersion":1,"themeMode":1,"futureField":42}"""
        val decoded = SettingsCodec.decode(withExtra)
        assertEquals(1, decoded?.themeMode)
    }

    @Test
    fun `范围收敛会把越界值夹回合法区间`() {
        val s = Settings(
            themeMode = 9,
            coverBlurDp = 100,
            coverAspect = 7,
            titleLines = 0,
            readerPreloadPages = 99,
            pageCacheLimitMb = 10,
            pageListTtlDays = 0,
            proxyPort = 99999,
        ).normalized()
        assertEquals(5, s.themeMode)
        assertEquals(40, s.coverBlurDp)
        assertEquals(2, s.coverAspect)
        assertEquals(1, s.titleLines)
        assertEquals(10, s.readerPreloadPages)
        assertEquals(100, s.pageCacheLimitMb)
        assertEquals(1, s.pageListTtlDays)
        assertEquals(65535, s.proxyPort)
    }

    @Test
    fun `旧版平铺 key 能迁移成 Settings`() {
        val legacy = object : LegacyReader {
            override fun getBool(key: String, default: Boolean): Boolean = when (key) {
                "blur_covers" -> false
                "exhentai" -> true
                "proxy_on" -> true
                else -> default
            }

            override fun getInt(key: String, default: Int): Int = when (key) {
                "theme_mode" -> 2
                "reader_preload_pages" -> 5
                "cover_blur_dp" -> 99   // 越界，迁移时应被收敛
                "proxy_port" -> 1080
                else -> default
            }

            override fun getString(key: String, default: String): String = when (key) {
                "proxy_host" -> "192.168.1.9"
                else -> default
            }
        }
        val migrated = SettingsMigration.fromLegacy(legacy)
        assertEquals(false, migrated.blurCovers)
        assertEquals(true, migrated.exHentai)
        assertEquals(true, migrated.proxyOn)
        assertEquals(2, migrated.themeMode)
        assertEquals(5, migrated.readerPreloadPages)
        assertEquals(40, migrated.coverBlurDp)
        assertEquals("192.168.1.9", migrated.proxyHost)
        assertEquals(1080, migrated.proxyPort)
        assertEquals("[]", migrated.filterRulesJson)
    }
}
