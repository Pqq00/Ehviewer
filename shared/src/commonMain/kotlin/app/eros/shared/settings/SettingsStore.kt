// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.settings

import kotlinx.serialization.json.Json

/** 设置文件的读写（平台无关部分在这里，落盘由 [SettingsStorage] 提供）。 */
internal object SettingsCodec {

    private val json = Json {
        ignoreUnknownKeys = true   // 旧版本多出来的字段不报错
        encodeDefaults = true      // 写全字段，便于人工检查
    }

    fun encode(settings: Settings): String = json.encodeToString(Settings.serializer(), settings)

    fun decode(raw: String?): Settings? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(Settings.serializer(), raw) }.getOrNull()
    }
}

/** 平台侧存储（Android=filesDir，桌面=用户目录）。 */
expect object SettingsStorage {
    fun read(): String?
    fun write(text: String)
}

/**
 * 旧版平铺 key → [Settings] 的迁移。
 *
 * 旧版把每个设置项存在 SharedPreferences / java.util.prefs 的独立 key 里，
 * 这里通过 [LegacyReader] 抽象读取，方便单测时注入假数据。
 */
interface LegacyReader {
    fun getBool(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun getString(key: String, default: String): String
}

internal object SettingsMigration {

    /** 旧版用过的全部平铺 key；只要其中一个存在就说明有可迁移的旧数据。 */
    val legacyKeys: List<String> = listOf(
        KEY_BLUR_COVERS, KEY_COVER_ASPECT, KEY_CACHE_LIMIT_MB, KEY_PRELOAD_PAGES,
        KEY_PAGELIST_TTL_DAYS, KEY_COVER_BLUR, KEY_TITLE_LINES, KEY_NET_LOG,
        KEY_EXHENTAI, KEY_PREDICTIVE_BACK, KEY_SWIPE_BACK, KEY_NAV_STYLE,
        KEY_NAV_MODE, KEY_SHOW_FPS, KEY_THEME_MODE, KEY_BAR_BLUR_ENABLED,
        KEY_BAR_BLUR_STYLE, KEY_TAG_TRANSLATION, KEY_FILTER_JSON, KEY_TAP_PAGING,
        KEY_PROXY_ON, KEY_PROXY_TYPE, KEY_PROXY_HOST, KEY_PROXY_PORT,
        KEY_NET_MAX_PER_HOST,
    )

    fun hasLegacyData(contains: (String) -> Boolean): Boolean = legacyKeys.any(contains)

    fun fromLegacy(reader: LegacyReader): Settings = Settings(
        blurCovers = reader.getBool(KEY_BLUR_COVERS, false),
        coverAspect = reader.getInt(KEY_COVER_ASPECT, 0),
        pageCacheLimitMb = reader.getInt(KEY_CACHE_LIMIT_MB, 500),
        readerPreloadPages = reader.getInt(KEY_PRELOAD_PAGES, 3),
        pageListTtlDays = reader.getInt(KEY_PAGELIST_TTL_DAYS, 7),
        coverBlurDp = reader.getInt(KEY_COVER_BLUR, 24),
        titleLines = reader.getInt(KEY_TITLE_LINES, 2),
        netLog = reader.getBool(KEY_NET_LOG, false),
        exHentai = reader.getBool(KEY_EXHENTAI, false),
        predictiveBack = reader.getBool(KEY_PREDICTIVE_BACK, false),
        swipeBack = reader.getBool(KEY_SWIPE_BACK, false),
        navBarStyle = reader.getInt(KEY_NAV_STYLE, 1),
        navBarMode = reader.getInt(KEY_NAV_MODE, 0),
        showFps = reader.getBool(KEY_SHOW_FPS, false),
        themeMode = reader.getInt(KEY_THEME_MODE, 0),
        barBlurEnabled = reader.getBool(KEY_BAR_BLUR_ENABLED, true),
        barBlurStyle = reader.getInt(KEY_BAR_BLUR_STYLE, 1),
        tagTranslation = reader.getBool(KEY_TAG_TRANSLATION, false),
        filterRulesJson = reader.getString(KEY_FILTER_JSON, "[]"),
        readerTapPaging = reader.getBool(KEY_TAP_PAGING, true),
        proxyOn = reader.getBool(KEY_PROXY_ON, false),
        proxyType = reader.getString(KEY_PROXY_TYPE, "system"),
        proxyHost = reader.getString(KEY_PROXY_HOST, "127.0.0.1"),
        proxyPort = reader.getInt(KEY_PROXY_PORT, 7890),
        netMaxPerHost = reader.getInt(KEY_NET_MAX_PER_HOST, 6),
    ).normalized()

    private const val KEY_BLUR_COVERS = "blur_covers"
    private const val KEY_COVER_ASPECT = "cover_aspect"
    private const val KEY_CACHE_LIMIT_MB = "cache_limit_mb"
    private const val KEY_PRELOAD_PAGES = "reader_preload_pages"
    private const val KEY_PAGELIST_TTL_DAYS = "pagelist_ttl_days"
    private const val KEY_COVER_BLUR = "cover_blur_dp"
    private const val KEY_TITLE_LINES = "title_lines"
    private const val KEY_NET_LOG = "net_log"
    private const val KEY_EXHENTAI = "exhentai"
    private const val KEY_PREDICTIVE_BACK = "predictive_back"
    private const val KEY_SWIPE_BACK = "swipe_back"
    private const val KEY_NAV_STYLE = "nav_bar_style"
    private const val KEY_NAV_MODE = "nav_bar_mode"
    private const val KEY_SHOW_FPS = "show_fps"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_BAR_BLUR_ENABLED = "bar_blur_enabled"
    private const val KEY_BAR_BLUR_STYLE = "bar_blur_style"
    private const val KEY_TAG_TRANSLATION = "tag_translation"
    private const val KEY_FILTER_JSON = "filter_rules"
    private const val KEY_TAP_PAGING = "reader_tap_paging"
    private const val KEY_PROXY_ON = "proxy_on"
    private const val KEY_PROXY_TYPE = "proxy_type"
    private const val KEY_PROXY_HOST = "proxy_host"
    private const val KEY_PROXY_PORT = "proxy_port"
    private const val KEY_NET_MAX_PER_HOST = "net_max_per_host"
}
