// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import app.eros.shared.ui.blend.ColorBlendToken

import top.yukonga.miuix.kmp.blur.textureBlur

import top.yukonga.miuix.kmp.blur.LayerBackdrop

import top.yukonga.miuix.kmp.blur.BlurDefaults

import top.yukonga.miuix.kmp.blur.BlurBlendMode

import top.yukonga.miuix.kmp.blur.BlendColorEntry

import top.yukonga.miuix.kmp.basic.CardDefaults

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.BlendMode

import androidx.compose.foundation.isSystemInDarkTheme

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eros.shared.BuildInfo
import app.eros.shared.auth.applyExHentai
import app.eros.shared.auth.isSignedIn
import app.eros.shared.auth.parseEhCookies
import app.eros.shared.auth.signIn
import app.eros.shared.auth.signInWithCookies
import app.eros.shared.auth.signOut
import app.eros.shared.data.SiteProfileUi
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.data.createEhApi
import app.eros.shared.settings.AppSettings
import app.eros.shared.system.showSystemToast
import app.eros.shared.ui.effect.BgEffectBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.ScreenCapture
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import app.eros.shared.ui.chrome.LocalAppBottomBarInset

/**
 * 账号页（从设置里独立出来）。
 *
 * 结构参照 miuix 官方示例的 About 页：`BgEffectBackground` 炫彩动态背景 + 顶部 logo 区 +
 * `SmallTitle` / `Card` 分组；滚动时背景渐隐、内容上移。
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
) {
    val barInset = LocalAppBottomBarInset.current
    val bgBackdrop = rememberBarBackdrop()
    val barBackdrop = rememberBarBackdrop()
    val pageScrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var signedIn by remember { mutableStateOf(isSignedIn()) }
    var working by remember { mutableStateOf(false) }
    var cookieLogin by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var showSitePicker by remember { mutableStateOf(false) }
    var accountId by remember(signedIn) { mutableStateOf(app.eros.shared.auth.signedInAccount()) }
    var displayName by remember(signedIn) { mutableStateOf<String?>(null) }
    LaunchedEffect(signedIn) {
        if (signedIn) {
            displayName = withContext(Dispatchers.IO) { app.eros.shared.auth.signedInDisplayName() }
            // 登录成功后登录卡整块消失，若还停在之前为键盘让位的滚动位置，
            // 顶部标题会压在顶栏下面，顺手滚回顶部。
            listState.animateScrollToItem(0)
        }
    }

    // 一聚焦输入框就把登录卡滚到视口顶部：Cookie 模式三行字段 + 按钮比可视区还高，
    // 键盘一弹，登录按钮就落到键盘下面点不到（这个窗口是 adjustResize，读 IME inset
    // 恒为 0，所以不能靠 inset 判断，只能在焦点变化时滚）。
    val bringLoginIntoView = Modifier.onFocusChanged { focus ->
        if (focus.isFocused) scope.launch { listState.animateScrollToItem(1) }
    }
    // 兜底：adjustResize 下窗口高度会塌陷，用列表高度变化判断键盘弹出（inset 读不到）。
    var maxListHeightPx by remember { mutableStateOf(0) }

    // 站点侧 Profile（uconfig 里的配置档案）：档案决定站点返回的页面长什么样，切换只改 cookie `sp`。
    val api = remember { createEhApi() }
    var profiles by remember { mutableStateOf<List<SiteProfileUi>>(emptyList()) }
    var profilesLoading by remember { mutableStateOf(false) }
    var showProfiles by remember { mutableStateOf(false) }
    LaunchedEffect(signedIn, AppSettings.exHentai) {
        if (!signedIn) {
            profiles = emptyList()
            return@LaunchedEffect
        }
        profilesLoading = true
        profiles = runCatching { withContext(Dispatchers.IO) { api.siteProfiles() } }.getOrDefault(emptyList())
        profilesLoading = false
    }

    val username = rememberTextFieldState()
    val password = rememberTextFieldState()
    val memberId = rememberTextFieldState()
    val passHash = rememberTextFieldState()
    val igneous = rememberTextFieldState()
    val clipboard = LocalClipboardManager.current

    // 密码输入时显示成圆点（输入内容仍是原文）
    val passwordMask = remember {
        object : OutputTransformation {
            override fun TextFieldBuffer.transformOutput() {
                if (length > 0) replace(0, length, "•".repeat(length))
            }
        }
    }

    fun applyClipboardCookies() {
        val raw = clipboard.getText()?.text.orEmpty()
        val parsed = parseEhCookies(raw)
        if (!parsed.any) {
            showSystemToast("未识别到登录 Cookie")
            return
        }
        parsed.memberId?.let { value -> memberId.edit { replace(0, length, value) } }
        parsed.passHash?.let { value -> passHash.edit { replace(0, length, value) } }
        parsed.igneous?.let { value -> igneous.edit { replace(0, length, value) } }
        showSystemToast(
            if (parsed.missing.isEmpty()) "已填入 Cookie"
            else "缺少 " + parsed.missing.joinToString("、"),
        )
    }


    ErosPageScaffold(
        title = "账号",
        onBack = onBack,
    ) { innerPadding, pageScrollBehavior, barBackdrop ->
        BgEffectBackground(
                dynamicBackground = true,
                modifier = Modifier.fillMaxSize(),
                bgModifier = if (bgBackdrop != null) Modifier.layerBackdrop(bgBackdrop) else Modifier,
                isFullSize = true,
                isOs3Effect = true,
                alpha = { 1f },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        // 键盘弹出时把列表让到键盘上方，否则 Cookie 模式三行字段会把
                        // 登录按钮压在键盘下面点不到。
                        .imePadding()
                        // 键盘弹出时窗口高度塌陷，借此把登录卡顶到视口顶部。
                        .onSizeChanged { size ->
                            if (size.height > maxListHeightPx) {
                                maxListHeightPx = size.height
                            } else if (!signedIn && maxListHeightPx - size.height > 300) {
                                scope.launch { listState.animateScrollToItem(1) }
                            }
                        }
                        .nestedScroll(pageScrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = barInset + innerPadding.calculateBottomPadding() + 24.dp,
                    ),
                ) {
                    // logo + 名称 + 状态（About 页头部）
                    item(key = "header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp, bottom = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val appIcon = LocalAppIcon.current
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        color = if (appIcon != null) Color.White else MiuixTheme.colorScheme.primary,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (appIcon != null) {
                                    // 图标是满底色方块，铺满容器（24dp 圆角由外层 clip 负责），
                                    // 不再留一圈白边。
                                    Image(
                                        painter = appIcon,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(
                                        imageVector = MiuixIcons.Contacts,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(48.dp),
                                    )
                                }
                            }
                            LogoTitle(BuildInfo.name, bgBackdrop)
                            Text(
                                if (signedIn) {
                                    "已登录 · " + (displayName?.takeIf { it.isNotBlank() }
                                        ?: accountId?.takeIf { it.isNotBlank() }
                                        ?: "-")
                                } else {
                                    "未登录"
                                },
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    // 账号状态（未登录时整块不显示）
                    if (signedIn) {
                        item(key = "status") {
                            SmallTitle("账号状态")
                            GlassCard(bgBackdrop, Modifier.padding(horizontal = 12.dp)) {
                                // 站点切换：E-Hentai（表站）/ ExHentai（里站）
                                ArrowPreference(
                                    title = "站点选择",
                                    summary = if (AppSettings.exHentai) "ExHentai" else "E-Hentai",
                                    onClick = { showSitePicker = true },
                                )
                                val currentProfile = profiles.firstOrNull { it.selected }?.name
                                ArrowPreference(
                                    title = "站点 Profile",
                                    summary = when {
                                        profilesLoading -> "加载中…"
                                        currentProfile != null -> currentProfile
                                        profiles.isEmpty() -> "点击从站点读取配置档案"
                                        else -> "点击切换配置档案"
                                    },
                                    onClick = { showProfiles = true },
                                )
                                ArrowPreference(
                                    title = "退出登录",
                                    summary = "清除 Cookie",
                                    onClick = { confirmSignOut = true },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    // 登录（已登录时不显示）
                    if (!signedIn) {
                    item(key = "login") {
                        SmallTitle("登录")
                        GlassCard(bgBackdrop, Modifier.padding(horizontal = 12.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (cookieLogin) {
                                    TextField(state = memberId, label = "ipb_member_id", modifier = bringLoginIntoView)
                                    TextField(state = passHash, label = "ipb_pass_hash", modifier = bringLoginIntoView)
                                    TextField(state = igneous, label = "igneous（可选）", modifier = bringLoginIntoView)
                                } else {
                                    TextField(state = username, label = "用户名 / 邮箱", modifier = bringLoginIntoView)
                                    TextField(
                                        state = password,
                                        label = "密码",
                                        modifier = bringLoginIntoView,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        outputTransformation = passwordMask,
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    // 左：在账号密码与 Cookie 两种登录方式之间切换
                                    IconButton(
                                        onClick = { cookieLogin = !cookieLogin },
                                        minHeight = 44.dp,
                                        minWidth = 44.dp,
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Background,
                                            contentDescription = if (cookieLogin) "切换到账号密码登录" else "切换到 Cookie 登录",
                                            tint = if (cookieLogin) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.onBackground,
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (working) return@Button
                                            working = true
                                            scope.launch {
                                                val result = withContext(Dispatchers.IO) {
                                                    if (cookieLogin) {
                                                        signInWithCookies(
                                                            memberId.text.toString(),
                                                            passHash.text.toString(),
                                                            igneous.text.toString().ifBlank { null },
                                                        )
                                                    } else {
                                                        signIn(username.text.toString(), password.text.toString())
                                                    }
                                                }
                                                showSystemToast(
                                                    if (result.ok) "登录成功"
                                                    else "登录失败：" + result.message.lineSequence().first().take(48),
                                                )
                                                signedIn = isSignedIn()
                                                if (signedIn) {
                                                    withContext(Dispatchers.IO) {
                                                        app.eros.shared.filter.HiddenTagStore.refresh()
                                                    }
                                                }
                                                working = false
                                            }
                                        },
                                        enabled = !working,
                                        colors = ButtonDefaults.buttonColorsPrimary(),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(if (working) "登录中…" else "登录")
                                    }
                                    // 右：从剪贴板填入 Cookie；密码模式下可见但不可点
                                    IconButton(
                                        onClick = { applyClipboardCookies() },
                                        enabled = cookieLogin,
                                        minHeight = 44.dp,
                                        minWidth = 44.dp,
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.ScreenCapture,
                                            contentDescription = "从剪贴板读取 Cookie",
                                            tint = if (cookieLogin) MiuixTheme.colorScheme.onBackground
                                            else MiuixTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    }

                    // Cookie（便于复制）
                    if (signedIn) {
                        item(key = "cookie") {
                            SmallTitle("Cookie")
                            GlassCard(bgBackdrop, Modifier.padding(horizontal = 12.dp)) {
                                CookieRow("ipb_member_id", app.eros.shared.auth.cookieValue("ipb_member_id"))
                                CookieRow("ipb_pass_hash", app.eros.shared.auth.cookieValue("ipb_pass_hash"))
                                CookieRow("igneous", app.eros.shared.auth.cookieValue("igneous"))
                                HorizontalDivider()
                                BasicComponent(
                                    title = "复制全部",
                                    onClick = {
                                        val cookie = buildString {
                                            app.eros.shared.auth.cookieValue("ipb_member_id")?.let { append("ipb_member_id=$it; ") }
                                            app.eros.shared.auth.cookieValue("ipb_pass_hash")?.let { append("ipb_pass_hash=$it; ") }
                                            app.eros.shared.auth.cookieValue("igneous")?.let { append("igneous=$it; ") }
                                        }.trim()
                                        app.eros.shared.platformCopy(cookie)
                                        showSystemToast("复制成功")
                                    },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
        }
    }

    // 站点 Profile 选择。切换只写 cookie `sp`，已经取到的列表不会变，所以提示用户下拉刷新。
    // 站点选择（E-Hentai / ExHentai）
    ErosBottomSheet(
        show = showSitePicker,
        title = "站点选择",
        insideMargin = DpSize(16.dp, 4.dp),
        onDismissRequest = { showSitePicker = false },
        backdrop = barBackdrop,
        onDismissFinished = { },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            SiteChoiceRow(
                name = "E-Hentai",
                description = "表站",
                selected = !AppSettings.exHentai,
                onClick = {
                    showSitePicker = false
                    if (!AppSettings.exHentai) {
                        AppSettings.exHentai = false
                        scope.launch {
                            withContext(Dispatchers.IO) { applyExHentai(false) }
                            showSystemToast("已切换到 E-Hentai")
                        }
                    }
                },
            )
            SiteChoiceRow(
                name = "ExHentai",
                description = "里站，需要登录",
                selected = AppSettings.exHentai,
                onClick = {
                    showSitePicker = false
                    if (!AppSettings.exHentai) {
                        AppSettings.exHentai = true
                        scope.launch {
                            withContext(Dispatchers.IO) { applyExHentai(true) }
                            showSystemToast("已切换到 ExHentai")
                        }
                    }
                },
            )
        }
    }

    ErosBottomSheet(
        show = showProfiles,
        title = "站点 Profile",
        insideMargin = DpSize(16.dp, 4.dp),
        onDismissRequest = { showProfiles = false },
        backdrop = barBackdrop,
        onDismissFinished = { },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            if (profiles.isEmpty()) {
                Text(
                    text = if (profilesLoading) "正在读取站点配置…" else "没有读到配置档案，请确认已登录",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            profiles.forEach { profile ->
                SiteProfileRow(
                    name = profile.name,
                    number = profile.number,
                    selected = profile.selected,
                    onClick = {
                        showProfiles = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                api.selectSiteProfile(profile.number)
                                // 换了档案，列表页的布局/缩略图/标题都可能变，旧列表页缓存不再可信
                                GalleryCache.clearListPages()
                            }
                            profiles = profiles.map { it.copy(selected = it.number == profile.number) }
                            // 通知已渲染的列表页重新拉取（缓存已在上面清掉）
                            AppSettings.bumpProfileRevision()
                            showSystemToast("已切换到 ${profile.name}，列表缓存已清空")
                        }
                    },
                )
            }
        }
    }

    // 退出登录：用 miuix 的 OverlayDialog（同一棵 Compose 树内的浮层），跟站点 Profile 弹层一致
    ErosDialog(
        show = confirmSignOut,
        title = "退出登录",
        summary = "退出后会清除本地登录 Cookie，需要重新登录才能访问里站。",
        onDismissRequest = { confirmSignOut = false },
        backdrop = barBackdrop,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { confirmSignOut = false },
                modifier = Modifier.weight(1f),
            ) {
                Text("取消", style = MiuixTheme.textStyles.button)
            }
            Button(
                onClick = {
                    confirmSignOut = false
                    scope.launch {
                        withContext(Dispatchers.IO) { signOut() }
                        // 未登录只能用表站：退出后把站点复位，避免下次启动还去请求里站。
                        AppSettings.exHentai = false
                        withContext(Dispatchers.IO) { applyExHentai(false) }
                        // 账号级隐藏标签跟着登录态走，退出后不能继续生效
                        app.eros.shared.filter.HiddenTagStore.clear()
                        signedIn = false
                        showSystemToast("已退出登录")
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("退出", style = MiuixTheme.textStyles.button)
            }
        }
    }
}
/** 站点选择行：名字 + 说明 + 选中打勾。 */
@Composable
private fun SiteChoiceRow(
    name: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = "当前站点",
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 站点 Profile 行：名字 + 选中打勾 + 编号（编号就是 cookie `sp` 的值）。 */
@Composable
private fun SiteProfileRow(
    name: String,
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = name,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = "当前 Profile",
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = number.toString(),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

/** 官方 About 页同款玻璃卡片：内容区域对背景做模糊 + 染色混合。 */
@Composable
private fun GlassCard(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val blend = remember(dark) {
        if (dark) ColorBlendToken.Overlay_Thin_Light else ColorBlendToken.Pured_Regular_Light
    }
    Card(
        modifier = modifier.then(
            if (backdrop != null) {
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(16.dp),
                    blurRadius = 60f,
                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                    colors = BlurDefaults.blurColors(blendColors = blend),
                )
            } else {
                Modifier
            },
        ),
        colors = CardDefaults.defaultColors(
            if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            // 不能给 Transparent：Card 会把它作为 LocalContentColor，TextField 的输入文字
            // 默认取该颜色，结果只剩光标可见、文字全透明。
            MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        content()
    }
}
/** 应用名称：字号与效果都对齐 miuix 官方示例「关于」页（35sp 加粗 + 玻璃字）。 */
@Composable
private fun LogoTitle(text: String, backdrop: LayerBackdrop?) {
    // 官方做法：文字自身当遮罩（DstIn），把背景模糊透进字形里，再叠三层染色，
    // 所以看起来不是纯色字而是一块玻璃。
    val dark = isSystemInDarkTheme()
    val logoBlend = remember(dark) {
        if (dark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }
    Text(
        text = text,
        modifier = if (backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RoundedCornerShape(16.dp),
                blurRadius = 150f,
                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                colors = BlurDefaults.blurColors(blendColors = logoBlend),
                contentBlendMode = BlendMode.DstIn,
            )
        } else {
            Modifier
        },
        color = MiuixTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        fontSize = 35.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

/** Cookie 行：名称 + 值（默认打码，点一下才显示原文）。 */
@Composable
private fun CookieRow(name: String, value: String?) {
    var revealed by remember(name) { mutableStateOf(false) }
    val text = value?.takeIf { it.isNotBlank() }
    val shown = when {
        text == null -> "（未设置）"
        revealed -> text
        // 三行统一用同样数量的点：长度一致才好对齐，也不泄露真实长度。
        else -> COOKIE_MASK
    }
    BasicComponent(
        title = name,
        summary = shown,
        onClick = if (text == null) null else ({ revealed = !revealed }),
    )
}

/** 打码占位：三行 Cookie 都用它，保证数量一致、左对齐。 */
private const val COOKIE_MASK = "••••••••"

