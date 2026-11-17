// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.settings

import kotlinx.serialization.Serializable

/**
 * 全部应用设置（单一数据类）。
 *
 * 以前是 40 多个平铺的 SharedPreferences key，加字段/导出/同步都要手写映射；
 * 现在整包序列化成 JSON 存一个文件，带 [schemaVersion] 便于以后迁移。
 */
@Serializable
data class Settings(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,

    // 外观
    val themeMode: Int = 0,
    val blurCovers: Boolean = false,
    val coverBlurDp: Int = 24,
    val coverAspect: Int = 0,
    val titleLines: Int = 2,
    /** 标题语言：0 = 英文优先，1 = 日文优先（所有界面跟随；详情页主标题跟随，副标题为另一种语言）。 */
    val galleryTitleMode: Int = 0,
    /** 列表卡片样式：classic / download。 */
    val cardStyle: String = "classic",
    val detailPrefetch: Boolean = true,

    /**
     * 首页分类行的条目（「热门」固定在最前，不在此列）。
     *
     * 内置分类和自定义分组混排在同一份列表里，顺序就是显示顺序；
     * 默认值见 [DEFAULT_HOME_ENTRIES]。
     */
    val homeEntries: List<HomeEntry> = DEFAULT_HOME_ENTRIES,

    // 顶栏 / 底栏
    val barBlurEnabled: Boolean = true,
    val barBlurStyle: Int = 1,
    val navBarStyle: Int = 1,
    val navBarMode: Int = 0,
    /** 宽屏（≥600dp）时用左侧 NavigationRail 代替底部导航栏。 */
    val navRail: Boolean = true,
    val showFps: Boolean = false,

    // 交互（预测式返回与侧滑返回已固定关闭，字段保留只为兼容旧配置文件）
    val predictiveBack: Boolean = false,
    val swipeBack: Boolean = false,
    val readerTapPaging: Boolean = true,

    // 阅读器
    val readerPreloadPages: Int = 3,
    val pageCacheLimitMb: Int = 500,
    val pageListTtlDays: Int = 7,
    /** 图片适应方式：0 适应宽度 / 1 适应高度 / 2 原始尺寸。 */
    val readerFit: Int = 0,
    /** 顶/底栏自动隐藏秒数，0 = 一直显示。 */
    val readerAutoHideSeconds: Int = 3,
    /** 音量键翻页。 */
    val readerVolumeKeys: Boolean = false,
    /** 双击缩放。 */
    val readerDoubleTapZoom: Boolean = true,
    /** 最大放大倍数 2..6。 */
    val readerMaxZoom: Int = 5,
    /** 按画廊分别记忆阅读模式与阅读进度。 */
    val readerPerGalleryMode: Boolean = false,
    /** 阅读器里隐藏系统状态栏（沉浸阅读）。 */
    val readerHideStatusBar: Boolean = false,
    val downloadGalleryConcurrency: Int = 2,
    val downloadPageConcurrency: Int = 3,

    // 站点 / 网络
    val exHentai: Boolean = false,
    /** 网络请求日志（环形缓冲 500 条，导出诊断包时带上）。默认开，出问题才有据可查。 */
    val netLog: Boolean = true,
    // 标签翻译与排序补全都默认关闭：两者的数据分别要 4 MB / 几十 KB，
    // 不想在用户没表达意愿时自动下载。
    val tagTranslation: Boolean = false,
    val tagCountEnabled: Boolean = false,
    /** 标签库服务端版本标记（ETag/Last-Modified），用于「关→开」时避免重复下载。 */
    val tagDbStamp: String? = null,
    val proxyOn: Boolean = false,
    val proxyType: String = "system",
    val proxyHost: String = "127.0.0.1",
    val proxyPort: Int = 7890,
    val netMaxPerHost: Int = 6,
    val maxRequestsPerSecond: Int = 6,
    val domainFronting: Boolean = false,

    // 过滤规则（保持 JSON 字符串，避免设置模型依赖 UI 模型）
    val filterRulesJson: String = "[]",

    // 账号里「隐藏」的关注标签：同步一份快照，离线也能过滤
    val useHiddenTags: Boolean = true,
    val hiddenTagRulesJson: String = "[]",
) {

    /** 统一做范围收敛，读取旧数据/手改文件后都能落到合法值。 */
    fun normalized(): Settings = copy(
        themeMode = themeMode.coerceIn(0, 5),
        coverBlurDp = coverBlurDp.coerceIn(0, 40),
        coverAspect = coverAspect.coerceIn(0, 2),
        titleLines = titleLines.coerceIn(1, 3),
        galleryTitleMode = galleryTitleMode.coerceIn(0, 1),
        cardStyle = if (cardStyle == "download") "download" else "classic",
        barBlurStyle = barBlurStyle.coerceIn(0, 1),
        navBarStyle = navBarStyle.coerceIn(0, 3),
        navBarMode = navBarMode.coerceIn(0, 2),
        readerPreloadPages = readerPreloadPages.coerceIn(0, 10),
        pageCacheLimitMb = pageCacheLimitMb.coerceIn(100, 2000),
        pageListTtlDays = pageListTtlDays.coerceIn(1, 90),
        readerFit = readerFit.coerceIn(0, 2),
        readerAutoHideSeconds = readerAutoHideSeconds.coerceIn(0, 10),
        readerMaxZoom = readerMaxZoom.coerceIn(2, 6),
        downloadGalleryConcurrency = downloadGalleryConcurrency.coerceIn(1, 3),
        downloadPageConcurrency = downloadPageConcurrency.coerceIn(1, 5),
        proxyPort = proxyPort.coerceIn(1, 65535),
        proxyType = if (proxyType in setOf("system", "http", "socks5", "direct")) proxyType else "system",
        netMaxPerHost = netMaxPerHost.coerceIn(2, 8),
        maxRequestsPerSecond = maxRequestsPerSecond.coerceIn(0, 20),
        schemaVersion = CURRENT_SCHEMA_VERSION,
    )

    companion object {
        /** 2：网络日志改为默认开启（旧配置里显式写着 false，加载时补一次）。 */
        const val CURRENT_SCHEMA_VERSION = 2
    }
}
