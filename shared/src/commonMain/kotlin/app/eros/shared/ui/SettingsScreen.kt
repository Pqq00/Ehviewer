// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package app.eros.shared.ui

import app.eros.shared.data.toEhError
import app.eros.shared.ui.design.AppSection
import app.eros.shared.ui.design.AppHint
import app.eros.shared.data.userMessage


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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import app.eros.shared.diag.NetLogUi
import app.eros.shared.filter.FilterMode
import app.eros.shared.filter.FilterRule
import app.eros.shared.isDebugBuild
import app.eros.shared.settings.AppSettings
import app.eros.shared.tags.initTagDatabase
import app.eros.shared.tags.saveTagDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
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
                SettingsAppearanceSection()
            }
        }
        item(key = "reading") {
            AppSection("阅读") {
                SettingsReadingSection()
            }
        }
        item(key = "storage") {
            AppSection("下载与存储") {
                SettingsStorageSection(
                    cacheUsage = cacheUsage,
                    onClearScope = { pendingClearScope = it },
                )
            }
        }

        item(key = "network") {
            AppSection("网络") {
                SettingsNetworkSection(
                    scope = scope,
                    proxyHost = proxyHost,
                    proxyPort = proxyPort,
                    onMessage = { authMessage = it },
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
                    SettingsDebugSection(onOpenNetLog = onOpenNetLog)
                }
            }
        }
        item(key = "about") {
            AppSection("关于") {
                // 导出日志放在「关于」而不是「调试」：release 包也需要能把问题带回来。
                SettingsAboutSection(scope)
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
