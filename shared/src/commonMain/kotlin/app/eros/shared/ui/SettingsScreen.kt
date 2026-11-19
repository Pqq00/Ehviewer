// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package app.eros.shared.ui

import app.eros.shared.data.toEhError
import app.eros.shared.ui.design.AppSection
import app.eros.shared.ui.design.AppHint
import app.eros.shared.data.userMessage


import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.preference.ArrowPreference

import androidx.compose.runtime.mutableIntStateOf


import top.yukonga.miuix.kmp.basic.ButtonDefaults

import top.yukonga.miuix.kmp.basic.Button

import top.yukonga.miuix.kmp.icon.extended.Search

import top.yukonga.miuix.kmp.icon.MiuixIcons

import top.yukonga.miuix.kmp.basic.IconButton

import top.yukonga.miuix.kmp.basic.Icon

import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

import top.yukonga.miuix.kmp.basic.ScrollBehavior

import androidx.compose.ui.input.nestedscroll.nestedScroll


import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.eros.shared.auth.applyExHentai
import app.eros.shared.auth.checkExHentai
import app.eros.shared.auth.isSignedIn
import app.eros.shared.auth.signIn
import app.eros.shared.auth.signInWithCookies
import app.eros.shared.auth.signOut
import app.eros.shared.data.createEhApi
import app.eros.shared.diag.exportDiagnostics
import app.eros.shared.diag.NetLogUi
import app.eros.shared.diag.setNetLogEnabled
import app.eros.shared.filter.FilterMode
import app.eros.shared.filter.FilterRule
import app.eros.shared.proxy.applyProxy
import app.eros.shared.reader.ReaderFit
import com.hippo.ehviewer.net.EhHttp
import app.eros.shared.settings.AppSettings
import app.eros.shared.system.showSystemToast
import app.eros.shared.tags.initTagDatabase
import app.eros.shared.tags.saveTagDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import app.eros.shared.ui.chrome.LocalAppBottomBarInset

/**
 * 设置页。结构照 npx 官方 example 的 SettingsPage：
 * `LazyColumn` + 每节一个 `SmallTitle` + `Card`，内容用 preference 组件；
 * 控件放在 Card 里，卡片之间留 12dp。
 */
@Composable
fun SettingsScreen(
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenWatched: () -> Unit,
    onOpenNetLog: () -> Unit,
    onOpenFilter: () -> Unit,
) {
    val barInset = LocalAppBottomBarInset.current
    val scope = rememberCoroutineScope()

    // 通用操作提示（代理 / 标签库更新等）
    var authMessage by remember { mutableStateOf<String?>(null) }

    var tagCount by remember { mutableStateOf(com.hippo.ehviewer.client.EhTagDatabase.size) }
    var updating by remember { mutableStateOf(false) }
    var freqVersion by remember { mutableStateOf<String?>(null) }
    var freqUpdating by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0f) }

    /**
     * 拉标签翻译库（关→开触发）。先做一次 HEAD 比对版本标记，
     * 服务端没变就不下载那 4 MB。
     */
    fun refreshTagDatabase() {
        if (updating) return
        updating = true
        updateProgress = 0f
        scope.launch {
            try {
                val result = createEhApi().fetchTagDatabaseIfChanged(AppSettings.tagDbStamp) { p ->
                    updateProgress = p.coerceIn(0f, 1f)
                }
                result.stamp?.let { AppSettings.tagDbStamp = it }
                val json = result.json
                if (json == null) {
                    authMessage = "标签库已是最新"
                } else {
                    saveTagDatabase(json)
                    tagCount = app.eros.shared.tags.TagDatabase.reload()
                    authMessage = "标签数据库已更新：" + tagCount + " 条"
                }
            } catch (t: Throwable) {
                // 失败就把标记清掉，下次「关→开」重新尝试完整下载。
                AppSettings.tagDbStamp = null
                authMessage = "标签库更新失败：" + t.toEhError().userMessage()
            } finally {
                updating = false
            }
        }
    }

    /** 拉标签词频（关→开触发）。服务端带版本号，已是最新时零下载。 */
    fun refreshTagCounts() {
        if (freqUpdating) return
        freqUpdating = true
        scope.launch {
            val r = app.eros.shared.data.updateTagCounts(force = false)
            freqUpdating = false
            if (r != null) {
                freqVersion = r.version
                authMessage = if (r.skipped) "词频已是最新" else "词频已更新：" + r.count + " 条"
            } else {
                authMessage = "词频更新失败"
            }
        }
    }

    LaunchedEffect(Unit) {
        freqVersion = app.eros.shared.data.cache.GalleryCache.tagCountMeta()?.version
        // 默认关闭；只有用户之前打开过、但本地还没有数据时才补一次。
        if (AppSettings.tagCountEnabled && freqVersion == null) refreshTagCounts()
        if (AppSettings.tagTranslation && tagCount == 0) refreshTagDatabase()
    }

    val proxyHost = rememberTextFieldState(AppSettings.proxyHost)
    val proxyPort = rememberTextFieldState(AppSettings.proxyPort.toString())
    val username = rememberTextFieldState()
    val password = rememberTextFieldState()

    var netLogOn by remember { mutableStateOf(AppSettings.netLogEnabled) }
    var pendingClearScope by remember { mutableStateOf<app.eros.shared.data.cache.CacheScope?>(null) }
    var cacheVersion by remember { mutableIntStateOf(0) }
    var cacheUsage by remember { mutableStateOf(app.eros.shared.data.cache.CacheUsage()) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(cacheVersion) {
        // 分区统计：HTTP 原始响应 / 解析模型 / 页地址 / 阅读图片 / Coil 图片加载器。
        cacheUsage = runCatching {
            app.eros.shared.data.cache.CacheManager.usage()
        }.getOrDefault(app.eros.shared.data.cache.CacheUsage())
    }

    ErosPageScaffold(
        title = "设置",
        navigationIcon = { AccountEntry(onOpenAccount) },
        actions = { HistoryAndSearchEntries(onOpenHistory, onOpenSearch) },
    ) { innerPadding, pageScrollBehavior, pageBackdrop ->
    // 内容由 ErosPageScaffold 记录成一层，顶栏模糊从这一层采样。
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
    val contentPadding = PaddingValues(
        top = innerPadding.calculateTopPadding() + ErosDimens.topContentGap,
        bottom = barInset + innerPadding.calculateBottomPadding() + ErosDimens.pagePadding,
    )
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pageScrollBehavior.nestedScrollConnection),
        contentPadding = contentPadding,
    ) {

        // ---------------------------------------------------------------- 封面
        item(key = "appearance") {
            AppSection("外观") {
                SwitchPreference(
                    title = "模糊封面",
                    checked = AppSettings.blurCovers,
                    onCheckedChange = { AppSettings.blurCovers = it },
                )
                ErosDropdownPreference(
                    title = "列表布局",
                    // 持久化值仍是 classic / download。
                    items = listOf("网格", "列表"),
                    selectedIndex = if (AppSettings.cardStyle == "download") 1 else 0,
                    onSelectedIndexChange = {
                        AppSettings.cardStyle = if (it == 1) "download" else "classic"
                    },
                )


                ErosDropdownPreference(
                    title = "标题语言",
                    items = listOf("英文优先", "日文优先"),
                    selectedIndex = AppSettings.galleryTitleMode,
                    onSelectedIndexChange = { AppSettings.galleryTitleMode = it },
                )
                ErosDropdownPreference(
                    title = "主题模式",
                    items = ThemeModeEntry.entries.map { it.label },
                    selectedIndex = AppSettings.themeMode,
                    onSelectedIndexChange = { AppSettings.themeMode = it },
                )

                ErosDropdownPreference(
                    title = "导航栏样式",
                    items = NavBarStyleEntry.entries.map { it.label },
                    selectedIndex = AppSettings.navBarStyle,
                    onSelectedIndexChange = { AppSettings.navBarStyle = it },
                )
                // 「导航项显示」只对固定/悬浮两种原生样式生效；iOS 与玻璃样式的图标与文字恒定显示
                AnimatedVisibility(visible = AppSettings.navBarStyle != 2 && AppSettings.navBarStyle != 3) {
                    ErosDropdownPreference(
                        title = "导航项显示",
                        items = NavBarModeEntry.entries.map { it.label },
                        selectedIndex = AppSettings.navBarMode,
                        onSelectedIndexChange = { AppSettings.navBarMode = it },
                    )
                }
                // 只在会真正触发侧边导航栏的宽度（≥600dp）上暴露这个开关，
                // 手机上它永远命中不了，放着只会让人误以为能开。
                AnimatedVisibility(visible = rememberNavigationRailBreakpointReached()) {
                    SwitchPreference(
                        title = "侧边导航栏",
                        checked = AppSettings.navRail,
                        onCheckedChange = { AppSettings.navRail = it },
                    )
                }
                SwitchPreference(
                    title = "栏模糊",
                    summary = "Android 13+",
                    checked = AppSettings.barBlurEnabled,
                    onCheckedChange = { AppSettings.barBlurEnabled = it },
                )
                AnimatedVisibility(visible = AppSettings.barBlurEnabled) {
                    ErosDropdownPreference(
                        title = "顶栏模糊样式",
                        items = BarBlurStyle.entries.map { it.label },
                        selectedIndex = AppSettings.barBlurStyle,
                        onSelectedIndexChange = { AppSettings.barBlurStyle = it },
                    )
                }

            }
        }
        item(key = "reading") {
            AppSection("阅读") {
                ErosDropdownPreference(
                    title = "预加载页数",
                    items = listOf("0 页", "3 页", "5 页", "10 页"),
                    selectedIndex = when (AppSettings.readerPreloadPages) {
                        0 -> 0
                        5 -> 2
                        10 -> 3
                        else -> 1
                    },
                    onSelectedIndexChange = {
                        AppSettings.readerPreloadPages = listOf(0, 3, 5, 10)[it]
                    },
                )
                ErosDropdownPreference(
                    title = "位图缓存",
                    items = listOf("32 MB", "64 MB", "128 MB", "256 MB", "512 MB"),
                    selectedIndex = when (AppSettings.bitmapCacheMb) {
                        32 -> 0
                        64 -> 1
                        256 -> 3
                        512 -> 4
                        else -> 2
                    },
                    onSelectedIndexChange = {
                        AppSettings.bitmapCacheMb = listOf(32, 64, 128, 256, 512)[it]
                    },
                )
                SwitchPreference(
                    title = "左右点按翻页",
                    checked = AppSettings.readerTapPaging,
                    onCheckedChange = { AppSettings.readerTapPaging = it },
                )
                ErosDropdownPreference(
                    title = "页面适应",
                    items = ReaderFit.entries.map { it.label },
                    selectedIndex = ReaderFit.fromOrdinal(AppSettings.readerFit).ordinal,
                    onSelectedIndexChange = { AppSettings.readerFit = it },
                )
                ErosDropdownPreference(
                    title = "自动隐藏顶底栏",
                    items = listOf("不隐藏", "2 秒", "3 秒", "5 秒"),
                    selectedIndex = when (AppSettings.readerAutoHideSeconds) {
                        0 -> 0
                        2 -> 1
                        5 -> 3
                        else -> 2
                    },
                    onSelectedIndexChange = {
                        AppSettings.readerAutoHideSeconds = listOf(0, 2, 3, 5)[it]
                    },
                )
                SwitchPreference(
                    title = "音量键翻页",
                    checked = AppSettings.readerVolumeKeys,
                    onCheckedChange = { AppSettings.readerVolumeKeys = it },
                )
                SwitchPreference(
                    title = "双击缩放",
                    checked = AppSettings.readerDoubleTapZoom,
                    onCheckedChange = { AppSettings.readerDoubleTapZoom = it },
                )
                ErosDropdownPreference(
                    title = "最大缩放",
                    items = listOf("2×", "3×", "4×", "5×", "6×"),
                    selectedIndex = (AppSettings.readerMaxZoom - 2).coerceIn(0, 4),
                    onSelectedIndexChange = { AppSettings.readerMaxZoom = it + 2 },
                )
                SwitchPreference(
                    title = "按画廊记忆模式",
                    checked = AppSettings.readerPerGalleryMode,
                    onCheckedChange = { AppSettings.readerPerGalleryMode = it },
                )

            }
        }
        item(key = "storage") {
            AppSection("下载与存储") {
                ErosDropdownPreference(
                    title = "同时下载",
                    items = listOf("1 个", "2 个", "3 个"),
                    selectedIndex = (AppSettings.downloadGalleryConcurrency - 1).coerceIn(0, 2),
                    onSelectedIndexChange = {
                        AppSettings.downloadGalleryConcurrency = it + 1
                    },
                )

                ErosDropdownPreference(
                    title = "单画廊并发",
                    items = listOf("1 页", "2 页", "3 页", "5 页"),
                    selectedIndex = when (AppSettings.downloadPageConcurrency) {
                        1 -> 0
                        2 -> 1
                        5 -> 3
                        else -> 2
                    },
                    onSelectedIndexChange = {
                        AppSettings.downloadPageConcurrency = listOf(1, 2, 3, 5)[it]
                    },
                )

                SwitchPreference(
                    title = "仅 Wi-Fi 预取",
                    summary = "移动数据下不预取封面与详情，滑到哪加载到哪",
                    checked = AppSettings.prefetchWifiOnly,
                    onCheckedChange = { AppSettings.prefetchWifiOnly = it },
                )

                app.eros.shared.data.cache.CacheScope.entries.forEach { scope ->
                    val summary = if (scope == app.eros.shared.data.cache.CacheScope.ImageLoader) {
                        if (cacheUsage.imageLoaderKnown) {
                            "内存 " + formatCacheSize(cacheUsage.imageLoaderMemory) +
                                " · 磁盘 " + formatCacheSize(cacheUsage.imageLoaderDisk)
                        } else {
                            "此平台不可用"
                        }
                    } else {
                        formatCacheSize(cacheUsage.of(scope)) + " · " + scope.summary
                    }
                    ArrowPreference(
                        title = scope.title,
                        summary = summary,
                        onClick = { pendingClearScope = scope },
                    )
                }

            }
        }

        item(key = "network") {
            AppSection("网络") {
                var proxyType by remember { mutableStateOf(AppSettings.proxyType) }
                ErosDropdownPreference(
                    title = "代理类型",
                    items = listOf("跟随系统", "HTTP", "SOCKS5", "直连"),
                    selectedIndex = when (proxyType) {
                        "http" -> 1
                        "socks5" -> 2
                        "direct" -> 3
                        else -> 0
                    },
                    onSelectedIndexChange = { index ->
                        val type = listOf("system", "http", "socks5", "direct")[index]
                        proxyType = type
                        AppSettings.proxyType = type
                        applyProxy(type, AppSettings.proxyHost, AppSettings.proxyPort)
                        authMessage = when (type) {
                            "http" -> "代理：HTTP ${AppSettings.proxyHost}:${AppSettings.proxyPort}"
                            "socks5" -> "代理：SOCKS5 ${AppSettings.proxyHost}:${AppSettings.proxyPort}"
                            "direct" -> "代理：直连"
                            else -> "代理：跟随系统"
                        }
                    },
                )
                if (proxyType == "http" || proxyType == "socks5") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextField(state = proxyHost, label = "代理主机（如 127.0.0.1）")
                        TextField(state = proxyPort, label = "端口（如 7890）")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            ActionText("应用代理") {
                                val host = proxyHost.text.toString().trim()
                                val port = proxyPort.text.toString().trim().toIntOrNull()
                                if (host.isBlank() || port == null || port !in 1..65535) {
                                    authMessage = "代理地址不合法"
                                } else {
                                    AppSettings.proxyHost = host
                                    AppSettings.proxyPort = port
                                    applyProxy(proxyType, host, port)
                                    authMessage = "已应用代理 $host:$port"
                                }
                            }
                        }
                    }
                }
                ErosDropdownPreference(
                    title = "同主机并发数",
                    items = listOf("2", "4", "6", "8"),
                    selectedIndex = when (AppSettings.netMaxPerHost) {
                        2 -> 0
                        4 -> 1
                        8 -> 3
                        else -> 2
                    },
                    onSelectedIndexChange = { index ->
                        val n = listOf(2, 4, 6, 8)[index]
                        AppSettings.netMaxPerHost = n
                        EhHttp.update(maxRequestsPerHost = n)
                        authMessage = "同主机并发数：$n"
                    },
                )
                ErosDropdownPreference(
                    title = "全局限速",
                    summary = "过高会被限流",
                    items = listOf("不限", "4 次/秒", "6 次/秒", "8 次/秒", "12 次/秒"),
                    selectedIndex = when (AppSettings.maxRequestsPerSecond) {
                        0 -> 0
                        4 -> 1
                        8 -> 3
                        12 -> 4
                        else -> 2
                    },
                    onSelectedIndexChange = { index ->
                        val n = listOf(0, 4, 6, 8, 12)[index]
                        AppSettings.maxRequestsPerSecond = n
                        EhHttp.update(maxRequestsPerSecond = n)
                        authMessage = if (n == 0) "全局限速：不限" else "全局限速：$n 次/秒"
                    },
                )
                SwitchPreference(
                    title = "固定 IP 解析",
                    summary = "绕开 DNS 污染",
                    checked = AppSettings.domainFronting,
                    onCheckedChange = { enabled ->
                        AppSettings.domainFronting = enabled
                        EhHttp.update(domainFronting = enabled)
                        if (!enabled) {
                            authMessage = "域名前置：关"
                        } else {
                            authMessage = "正在检测域名前置…"
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { EhHttp.testConnection(6000) }
                                if (result.startsWith("连接失败")) {
                                    AppSettings.domainFronting = false
                                    EhHttp.update(domainFronting = false)
                                }
                                authMessage = "域名前置：$result"
                            }
                        }
                    },
                )
            }
        }
        item(key = "tags") {
            AppSection("标签与过滤") {
                // 两个开关本身就是「刷新」手势：关→开触发一次更新，副标题承担状态显示，
                // 所以这里没有「更新词频 / 更新数据库」按钮。
                val translationOn = AppSettings.tagTranslation
                SwitchPreference(
                    title = "标签中文翻译",
                    summary = when {
                        !translationOn -> "开启后自动下载"
                        updating -> "更新中 " + (updateProgress * 100).toInt() + "%"
                        tagCount > 0 -> "已同步 " + tagCount + " 条"
                        else -> "尚未下载"
                    },
                    checked = translationOn,
                    onCheckedChange = { enabled ->
                        AppSettings.tagTranslation = enabled
                        if (enabled) refreshTagDatabase()
                    },
                )
                if (updating) {
                    LinearProgressIndicator(
                        progress = updateProgress.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                val countOn = AppSettings.tagCountEnabled
                SwitchPreference(
                    title = "标签排序补全",
                    summary = when {
                        !countOn -> "开启后自动下载"
                        freqUpdating -> "更新中…"
                        freqVersion != null -> freqVersion.orEmpty()
                        else -> "尚未下载"
                    },
                    checked = countOn,
                    onCheckedChange = { enabled ->
                        AppSettings.tagCountEnabled = enabled
                        if (enabled) refreshTagCounts()
                    },
                )

                var useHidden by remember { mutableStateOf(AppSettings.useHiddenTags) }
                SwitchPreference(
                    title = "账号隐藏标签",
                    summary = "已同步 " +
                        app.eros.shared.filter.HiddenTagStore.count + " 条",
                    checked = useHidden,
                    onCheckedChange = {
                        useHidden = it
                        AppSettings.useHiddenTags = it
                    },
                )
                ArrowPreference(
                    title = "过滤规则",
                    summary = AppSettings.filterRules.size.toString() + " 条",
                    onClick = { onOpenFilter() },
                )

                ArrowPreference(
                    title = "关注标签",
                    summary = "需登录",
                    onClick = { onOpenWatched() },
                )
            }
        }
        // 调试组只在 debug 包里出现：release 不暴露 FPS / 网络日志。
        if (app.eros.shared.isDebugBuild) {
            item(key = "debug") {
                AppSection("调试") {
                    SwitchPreference(
                        title = "显示 FPS",
                        checked = AppSettings.showFps,
                        onCheckedChange = { AppSettings.showFps = it },
                    )
                    SwitchPreference(
                        title = "网络日志",
                        checked = netLogOn,
                        onCheckedChange = {
                            netLogOn = it
                            AppSettings.netLogEnabled = it
                            setNetLogEnabled(it)
                        },
                    )
                    AnimatedVisibility(visible = netLogOn) {
                        ArrowPreference(
                            title = "查看日志",
                            onClick = { onOpenNetLog() },
                        )
                    }
                    // 只用于验证崩溃日志这条链路：点一下会立刻闪退，下次启动能看到崩溃提示。
                    ArrowPreference(
                        title = "模拟崩溃",
                        onClick = { throw IllegalStateException("诊断测试：模拟崩溃") },
                    )
                }
            }
        }
        item(key = "about") {
            AppSection("关于") {
                // 导出日志放在「关于」而不是「调试」：release 包也需要能把问题带回来。
                ArrowPreference(
                    title = "导出日志",
                    onClick = {
                        scope.launch { showSystemToast(exportDiagnostics(share = false)) }
                    },
                )
                ArrowPreference(
                    title = "分享日志",
                    onClick = {
                        scope.launch { showSystemToast(exportDiagnostics(share = true)) }
                    },
                )
                BasicComponent(
                    title = "版本",
                    summary = "${app.eros.shared.BuildInfo.version} (${app.eros.shared.BuildInfo.versionCode})",
                )
            }
        }
    }
    // 与官方设置页示例一致：内容右侧叠一条竖向滚动条。
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        trackPadding = contentPadding,
    )
    }

    // 设置页在 ErosPageScaffold 内，能拿到 Scaffold 的 popup 宿主，所以这里用 miuix 原生
    // OverlayDialog：420dp 最大宽度、32dp 圆角、DialogDefaults 的配色与进出场动画都由官方实现给出。
    // TrackOverlay 让弹窗显示期间隐藏底部导航栏（下载页「删除下载」同样处理），否则底栏会盖住按钮。
    // 分区清理：每个缓存 scope 一个确认框，只删这一类，不动其它层。
    val scopeToClear = pendingClearScope
    TrackOverlay(scopeToClear != null)
    ErosDialog(
        backdrop = LocalPageBackdrop.current,
        show = scopeToClear != null,
        title = "清理" + (scopeToClear?.title ?: "缓存"),
        summary = (scopeToClear?.summary ?: "") + "，删除后需重新缓存",
        onDismissRequest = { pendingClearScope = null },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { pendingClearScope = null },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
            ) { Text("取消", style = MiuixTheme.textStyles.button) }
            Button(
                onClick = {
                    val clearing = scopeToClear
                    pendingClearScope = null
                    if (clearing != null) {
                        scope.launch {
                            app.eros.shared.data.cache.CacheManager.clear(clearing)
                            cacheVersion++
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onPrimary,
                ),
            ) { Text("清理", style = MiuixTheme.textStyles.button) }
        }
    }

}

}
/** 卡内动作按钮：用 miuix 官方 Button（次要色 + 标准按压反馈）。 */
@Composable
private fun ActionText(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        minHeight = 34.dp,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 14.dp,
            vertical = 6.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MiuixTheme.colorScheme.primary,
        ),
    ) {
        Text(label, style = MiuixTheme.textStyles.button)
    }
}
/** 字节数 → 人类可读（MB/KB）。 */
private fun formatCacheSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
