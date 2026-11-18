// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.eros.shared.data.WatchedTagUi
import com.hippo.ehviewer.util.AppLog
import app.eros.shared.data.EhApi
import app.eros.shared.data.toEhError
import app.eros.shared.data.withEhErrors
import app.eros.shared.data.userMessage
import app.eros.shared.data.createEhApi
import app.eros.shared.system.showSystemToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 关注标签页状态（`/watched`，需要登录）。 */
class WatchedState(
    private val api: EhApi = createEhApi(),
) {

    private val source = api
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var items by mutableStateOf<List<WatchedTagUi>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** 是否已登录；未登录直接提示，不发请求。 */
    var signedIn by mutableStateOf(true)
        private set

    private var loadedOnce = false

    fun ensureLoaded() {
        if (loadedOnce) return
        loadedOnce = true
        signedIn = source.isSignedIn()
        if (!signedIn) {
            loading = false
            return
        }
        reload()
    }

    fun reload() {
        signedIn = source.isSignedIn()
        if (!signedIn) {
            loading = false
            return
        }
        scope.launch {
            loading = true
            error = null
            val tags = withEhErrors({ error = it }) { source.watchedTags() }
            if (tags != null) {
                items = tags
                app.eros.shared.filter.HiddenTagStore.update(tags)
                AppLog.i("Watched", "loaded=${items.size}")
            } else {
                AppLog.w("Watched", "FAILED: $error")
            }
            loading = false
        }
    }

    fun addTag(tag: String, watched: Boolean, hidden: Boolean) {
        val name = tag.trim()
        if (name.isBlank() || busy) return
        scope.launch {
            busy = true
            try {
                if (items.any { it.tag.equals(name, ignoreCase = true) }) {
                    showSystemToast("该标签已存在：$name")
                    busy = false
                    return@launch
                }
                val posted = source.addWatchedTag(name, watched, hidden)
                AppLog.i("Watched", "add POST returned=${posted.size} hasNew=${posted.any { it.tag.equals(name, true) }}")
                // POST 的返回可能不含新标签（服务端渲染时点问题），再加一次 GET 以服务端为准
                val fresh = source.watchedTags()
                items = fresh
                app.eros.shared.filter.HiddenTagStore.update(fresh)
                val ok = fresh.any { it.tag.equals(name, true) }
                AppLog.i("Watched", "after GET total=${fresh.size} hasNew=$ok")
                showSystemToast(if (ok) "已添加：$name" else "添加失败：标签可能已存在或格式不对")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                showSystemToast("添加失败：${t.toEhError().userMessage()}")
                AppLog.w("Watched", "add FAILED: ${t.message}")
            } finally {
                busy = false
            }
        }
    }

    fun deleteTag(item: WatchedTagUi) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                items = source.deleteWatchedTag(item.id, item.weight)
                app.eros.shared.filter.HiddenTagStore.update(items)
                showSystemToast("已删除：${item.tag}")
                AppLog.i("Watched", "deleted=${item.tag} total=${items.size}")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                showSystemToast("删除失败：${t.toEhError().userMessage()}")
                AppLog.w("Watched", "delete FAILED: ${t.message}")
            } finally {
                busy = false
            }
        }
    }
}

/**
 * 关注标签列表：点标签去搜索；右上角 + 从底部滑出添加面板；每行「删除」点两次确认。
 *
 * 注意：`OverlayBottomSheet` 必须声明在 `Scaffold` 的 content 内部，
 * 否则拿不到 popup 宿主（表现为"点了没反应"）。
 */
@Composable
fun WatchedScreen(
    state: WatchedState,
    onBack: () -> Unit,
    onSearchTag: (String) -> Unit,
) {
    LaunchedEffect(state) { state.ensureLoaded() }

    var showAdd by remember { mutableStateOf(false) }
    var addText by remember { mutableStateOf("") }
    var addWatched by remember { mutableStateOf(true) }
    var addHidden by remember { mutableStateOf(false) }
    var pendingDeleteTag by remember { mutableStateOf<String?>(null) }

    ErosPageScaffold(
        title = "关注标签",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showAdd = true }) {
                Icon(imageVector = MiuixIcons.Add, contentDescription = "添加")
            }
        },
    ) { innerPadding, scrollBehavior, _ ->
        Box(Modifier.fillMaxSize()) {
            when {
                !state.signedIn -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("需要登录（设置 → 账号）", style = MiuixTheme.textStyles.body2)
                }

                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.error != null -> Box(
                    Modifier
                        .fillMaxSize()
                        .clickable { state.reload() },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加载失败，点按重试", style = MiuixTheme.textStyles.body1)
                        Text(
                            state.error ?: "",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }

                state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("已登录，但还没有关注标签，点右上角 + 添加", style = MiuixTheme.textStyles.body2)
                }

                else -> LazyColumn(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.id.ifBlank { it.tag } }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSearchTag(item.tag) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.tag, style = MiuixTheme.textStyles.body1)
                                    Text(
                                        buildString {
                                            append(if (item.watched) "已关注" else "未关注")
                                            if (item.hidden) append(" · 隐藏")
                                        },
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                                val confirming = pendingDeleteTag == item.id
                                Text(
                                    text = if (confirming) "确认删除" else "删除",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = if (confirming) MiuixTheme.colorScheme.error
                                    else MiuixTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable {
                                            if (confirming) {
                                                pendingDeleteTag = null
                                                state.deleteTag(item)
                                            } else {
                                                pendingDeleteTag = item.id
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 底部滑出面板（必须在 Scaffold content 内才有 popup 宿主）
            TrackOverlay(showAdd)
            ErosBottomSheet(
                show = showAdd,
                title = "添加关注标签",
                onDismissRequest = { showAdd = false },
                onDismissFinished = { },
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    TextField(
                        value = addText,
                        onValueChange = { addText = it },
                        label = "标签，例如 female:bikini",
                    )
                    // 站点表单里 watch / hide 是二选一，所以只暴露一个开关：
                    // 关闭 = 关注（默认），打开 = 仅隐藏不关注
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("仅隐藏（不关注）", style = MiuixTheme.textStyles.body2)
                            Text(
                                "关闭时发送「关注」，打开时发送「隐藏」",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Switch(checked = addHidden, onCheckedChange = { addHidden = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { showAdd = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(),
                        ) { Text("取消", style = MiuixTheme.textStyles.button) }
                        Button(
                            onClick = {
                                val name = addText
                                addText = ""
                                showAdd = false
                                state.addTag(name, watched = !addHidden, hidden = addHidden)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.primary,
                                contentColor = MiuixTheme.colorScheme.onPrimary,
                            ),
                        ) { Text("添加", style = MiuixTheme.textStyles.button) }
                    }
                }
            }
        }
    }
}
