// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.eros.shared.filter.FilterRule
import app.eros.shared.filter.GalleryFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 平台侧的布尔持久化（旧版平铺 key，仅用于迁移读取）。 */
expect fun platformGetBool(key: String, default: Boolean): Boolean

expect fun platformSetBool(key: String, value: Boolean)

/** 平台侧字符串/整数持久化（旧版平铺 key，仅用于迁移读取）。 */
expect fun platformGetString(key: String, default: String): String

expect fun platformSetString(key: String, value: String)

expect fun platformGetInt(key: String, default: Int): Int

expect fun platformSetInt(key: String, value: Int)

/** 旧版平铺 key 是否存在（用来区分「真·首次启动」和「有旧数据可迁移」）。 */
expect fun platformHasKey(key: String): Boolean

/**
 * 应用设置。
 *
 * 存储：整包 [Settings] 序列化成 `settings.json`（原子写）。
 * 首次启动若没有该文件，会从旧版平铺 key 迁移一次（旧 key 保留不删，可回退）。
 *
 * 对外暴露的属性签名保持不变，UI 侧无需改动。
 */
object AppSettings {

    private var state by mutableStateOf(Settings())
    private var loaded = false

    // ---------------------------------------------------------------- 运行时信号

    /**
     * 站点 Profile 切换计数。
     *
     * 只活在本进程内存里、不落盘：Profile 决定列表页的布局/缩略图/标题语言，
     * 切换后已经渲染出来的列表也得换掉，列表页监听这个值变化即可重新拉取。
     */
    var profileRevision by mutableStateOf(0)
        private set

    fun bumpProfileRevision() {
        profileRevision += 1
    }

    // ---------------------------------------------------------------- 外观

    var themeMode: Int
        get() = state.themeMode
        set(value) = update { it.copy(themeMode = value) }

    var blurCovers: Boolean
        get() = state.blurCovers
        set(value) = update { it.copy(blurCovers = value) }

    var coverBlurDp: Int
        get() = state.coverBlurDp
        set(value) = update { it.copy(coverBlurDp = value) }

    /** 封面比例：0 = 3:4，1 = 4:5，2 = 1:1。 */
    var coverAspect: Int
        get() = state.coverAspect
        set(value) = update { it.copy(coverAspect = value) }

    /** 卡片标题最多行数：1..3。 */
    var titleLines: Int
        get() = state.titleLines
        set(value) = update { it.copy(titleLines = value) }

    /** 列表卡片样式：classic / download。 */
    var cardStyle: String
        get() = state.cardStyle
        set(value) = update {
            it.copy(cardStyle = if (value == "download") "download" else "classic")
        }

    var detailPrefetch: Boolean
        get() = state.detailPrefetch
        set(value) = update { it.copy(detailPrefetch = value) }

    // ---------------------------------------------------------------- 顶栏 / 底栏

    var barBlurEnabled: Boolean
        get() = state.barBlurEnabled
        set(value) = update { it.copy(barBlurEnabled = value) }

    /** 顶栏模糊样式：0 高斯 / 1 渐进。 */
    var barBlurStyle: Int
        get() = state.barBlurStyle
        set(value) = update { it.copy(barBlurStyle = value) }

    /** 导航栏样式：0 固定 / 1 悬浮 / 2 iOS 风格悬浮胶囊。 */
    /** 详情页标题主语言：0 英文优先 / 1 日文优先。 */
    var galleryTitleMode: Int
        get() = state.galleryTitleMode
        set(value) = update { it.copy(galleryTitleMode = value) }

    var navBarStyle: Int
        get() = state.navBarStyle
        set(value) = update { it.copy(navBarStyle = value) }

    /** 导航项显示模式：0 图标+文字 / 1 仅图标 / 2 仅选中显示文字。 */
    var navBarMode: Int
        get() = state.navBarMode
        set(value) = update { it.copy(navBarMode = value) }

    /** 宽屏（≥600dp）时用左侧 NavigationRail 代替底部导航栏。 */
    var navRail: Boolean
        get() = state.navRail
        set(value) = update { it.copy(navRail = value) }

    var showFps: Boolean
        get() = state.showFps
        set(value) = update { it.copy(showFps = value) }

    // ---------------------------------------------------------------- 交互

    var predictiveBack: Boolean
        get() = state.predictiveBack
        set(value) = update { it.copy(predictiveBack = value) }

    var swipeBack: Boolean
        get() = state.swipeBack
        set(value) = update { it.copy(swipeBack = value) }

    var readerTapPaging: Boolean
        get() = state.readerTapPaging
        set(value) = update { it.copy(readerTapPaging = value) }

    // ---------------------------------------------------------------- 阅读器

    var readerPreloadPages: Int
        get() = state.readerPreloadPages
        set(value) = update { it.copy(readerPreloadPages = value) }

    var pageCacheLimitMb: Int
        get() = state.pageCacheLimitMb
        set(value) = update { it.copy(pageCacheLimitMb = value) }

    var pageListTtlDays: Int
        get() = state.pageListTtlDays
        set(value) = update { it.copy(pageListTtlDays = value) }

    /** 图片适应方式：0 适应宽度 / 1 适应高度 / 2 原始尺寸。 */
    var readerFit: Int
        get() = state.readerFit
        set(value) = update { it.copy(readerFit = value) }

    /** 顶/底栏自动隐藏秒数，0 = 一直显示。 */
    var readerAutoHideSeconds: Int
        get() = state.readerAutoHideSeconds
        set(value) = update { it.copy(readerAutoHideSeconds = value) }

    /** 音量键翻页。 */
    var readerVolumeKeys: Boolean
        get() = state.readerVolumeKeys
        set(value) = update { it.copy(readerVolumeKeys = value) }

    /** 双击缩放。 */
    var readerDoubleTapZoom: Boolean
        get() = state.readerDoubleTapZoom
        set(value) = update { it.copy(readerDoubleTapZoom = value) }

    /** 最大放大倍数 2..6。 */
    var readerMaxZoom: Int
        get() = state.readerMaxZoom
        set(value) = update { it.copy(readerMaxZoom = value) }

    /** 按画廊分别记忆阅读模式。 */
    var readerPerGalleryMode: Boolean
        get() = state.readerPerGalleryMode
        set(value) = update { it.copy(readerPerGalleryMode = value) }

    /** 阅读器里隐藏系统状态栏（沉浸阅读）。 */
    var readerHideStatusBar: Boolean
        get() = state.readerHideStatusBar
        set(value) = update { it.copy(readerHideStatusBar = value) }

    // ------------------------------------------------------------ 首页分类行

    /** 首页分类行的条目（「热门」固定在最前，不在这里）。 */
    var homeEntries: List<HomeEntry>
        get() = state.homeEntries
        set(value) = update { it.copy(homeEntries = value) }

    // ---------------------------------------------------------------- 下载

    /** 同时下载几个画廊（1..3）。 */
    var downloadGalleryConcurrency: Int
        get() = state.downloadGalleryConcurrency
        set(value) = update { it.copy(downloadGalleryConcurrency = value) }

    /** 单个画廊内同时下载几页（1..5）。 */
    var downloadPageConcurrency: Int
        get() = state.downloadPageConcurrency
        set(value) = update { it.copy(downloadPageConcurrency = value) }

    // ---------------------------------------------------------------- 站点 / 网络

    var exHentai: Boolean
        get() = state.exHentai
        set(value) = update { it.copy(exHentai = value) }

    var netLogEnabled: Boolean
        get() = state.netLog
        set(value) = update { it.copy(netLog = value) }

    var tagTranslation: Boolean
        get() = state.tagTranslation
        set(value) = update { it.copy(tagTranslation = value) }

    var tagCountEnabled: Boolean
        get() = state.tagCountEnabled
        set(value) = update { it.copy(tagCountEnabled = value) }

    /** 标签库服务端版本标记；null 表示还没成功比对过。 */
    var tagDbStamp: String?
        get() = state.tagDbStamp
        set(value) = update { it.copy(tagDbStamp = value) }

    /** system/http/socks5/direct；旧版只有 proxyOn=true 时按 HTTP 处理 */
    var proxyType: String
        get() {
            val type = state.proxyType
            return if (type == "system" && state.proxyOn) "http" else type
        }
        set(value) = update {
            it.copy(
                proxyType = if (value in setOf("system", "http", "socks5", "direct")) value else "system",
                proxyOn = value != "system",
            )
        }

    var netMaxPerHost: Int
        get() = state.netMaxPerHost
        set(value) = update { it.copy(netMaxPerHost = value.coerceIn(2, 8)) }

    var maxRequestsPerSecond: Int
        get() = state.maxRequestsPerSecond
        set(value) = update { it.copy(maxRequestsPerSecond = value.coerceIn(0, 20)) }

    var domainFronting: Boolean
        get() = state.domainFronting
        set(value) = update { it.copy(domainFronting = value) }

    var proxyHost: String
        get() = state.proxyHost
        set(value) = update { it.copy(proxyHost = value.trim()) }

    var proxyPort: Int
        get() = state.proxyPort
        set(value) = update { it.copy(proxyPort = value) }

    // ---------------------------------------------------------------- 过滤规则

    var filterRules: List<FilterRule>
        get() = GalleryFilter.parse(state.filterRulesJson)
        set(value) = update { it.copy(filterRulesJson = GalleryFilter.toJson(value)) }

    /** 是否把账号里「隐藏」的标签也当作过滤规则。 */
    var useHiddenTags: Boolean
        get() = state.useHiddenTags
        set(value) = update { it.copy(useHiddenTags = value) }

    var hiddenTagRulesJson: String
        get() = state.hiddenTagRulesJson
        set(value) = update { it.copy(hiddenTagRulesJson = value) }

    // ---------------------------------------------------------------- 读写

    /** 修改设置 → 收敛范围 → 合并后异步落盘（300ms 防抖）。 */
    private fun update(transform: (Settings) -> Settings) {
        state = transform(state).normalized()
        scheduleSave()
    }

    // ------------------------------------------------------------ 落盘调度
    //
    // 设置面板里的滑杆/开关会连续触发 update()，原先每次都整包 JSON 同步写盘，
    // 拖动一次封面模糊强度就能写出几十份 catalog。这里改成 300ms 防抖 + 合并：
    // 同一时刻最多只有一个写任务，期间的新改动只在任务循环里再写一次最新快照。

    private const val SAVE_DEBOUNCE_MS = 300L

    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var saveJob: Job? = null
    private var savePending = false

    private fun scheduleSave() {
        savePending = true
        if (saveJob?.isActive == true) return
        saveJob = saveScope.launch { drainSaves() }
    }

    private suspend fun drainSaves() {
        while (savePending) {
            delay(SAVE_DEBOUNCE_MS)
            if (!savePending) return
            savePending = false
            val snapshot = state
            runCatching { SettingsStorage.write(SettingsCodec.encode(snapshot)) }
                .onFailure { println("[Settings] save failed: ${it.message}") }
        }
    }

    /** 立即把待写状态落盘（退到后台/页面销毁前调用，避免防抖窗口内丢改动）。 */
    fun flush() {
        if (!savePending) return
        savePending = false
        val snapshot = state
        saveScope.launch {
            runCatching { SettingsStorage.write(SettingsCodec.encode(snapshot)) }
                .onFailure { println("[Settings] save failed: ${it.message}") }
        }
    }

    fun load() {
        if (loaded) return
        val raw = SettingsStorage.read()
        val restored = SettingsCodec.decode(raw)
        val needsSchemaUpgrade = restored != null && restored.schemaVersion < 2
        state = when {
            restored != null -> {
                // schema 1 → 2：网络日志改成默认开。旧文件里 netLog 是显式 false，
                // 靠默认值改不动，这里补一次（之后用户自己关掉会以 schema=2 落盘，不再覆盖）。
                val upgraded = if (needsSchemaUpgrade) {
                    println("[Settings] upgrade schema ${restored.schemaVersion} -> 2, 网络日志默认开")
                    restored.copy(netLog = true)
                } else {
                    restored
                }
                upgraded.normalized()
            }
            // 只有真的存在旧版 key 时才迁移；否则一律走 [Settings] 的默认值，
            // 避免「清除数据后第一次启动」被旧默认值（如模糊封面开、底栏固定）覆盖。
            SettingsMigration.hasLegacyData { platformHasKey(it) } -> {
                val migrated = SettingsMigration.fromLegacy(object : LegacyReader {
                    override fun getBool(key: String, default: Boolean) = platformGetBool(key, default)
                    override fun getInt(key: String, default: Int) = platformGetInt(key, default)
                    override fun getString(key: String, default: String) = platformGetString(key, default)
                })
                runCatching { SettingsStorage.write(SettingsCodec.encode(migrated)) }
                println("[Settings] migrated from legacy keys")
                migrated
            }
            else -> {
                val fresh = Settings()
                runCatching { SettingsStorage.write(SettingsCodec.encode(fresh)) }
                println("[Settings] no saved settings, using defaults")
                fresh
            }
        }
        loaded = true
        if (needsSchemaUpgrade) {
            // 立刻落盘，避免每次启动都再迁移一遍、也免得用户没改设置就永远停在旧 schema。
            runCatching { SettingsStorage.write(SettingsCodec.encode(state)) }
        }
        println("[Settings] loaded schema=${state.schemaVersion} theme=${state.themeMode}")
    }
}
