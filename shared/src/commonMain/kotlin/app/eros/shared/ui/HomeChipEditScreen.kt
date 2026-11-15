// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.currentTimeMillis
import app.eros.shared.settings.AppSettings
import app.eros.shared.settings.HOME_CATEGORY_CHOICES
import app.eros.shared.settings.HomeEntry
import app.eros.shared.settings.MAX_CUSTOM_GROUPS
import app.eros.shared.system.showSystemToast
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首页分类行条目的编辑页。
 *
 * - [entryKey] 传 null / "new" 时是新建自定义分组；
 * - 传 "cat:<mask>" 时是内置分类，只允许改名、上下移动、删除；
 * - 传 "g:<id>" 时是自定义分组，可以改名称、包含/排除标签、限定类别。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeChipEditScreen(
    entryKey: String?,
    onBack: () -> Unit,
) {
    val entries = AppSettings.homeEntries
    val isNew = entryKey.isNullOrBlank() || entryKey == "new"
    val index = if (isNew) -1 else entries.indexOfFirst { it.key == entryKey }
    val existing = entries.getOrNull(index)
    // 新建时一定是分组；编辑内置分类时只改名字，标签区不出现。
    val isGroup = isNew || existing?.isGroup == true

    var label by remember(entryKey) { mutableStateOf(existing?.label.orEmpty()) }
    var includeTags by remember(entryKey) {
        mutableStateOf(existing?.includeTags.orEmpty())
    }
    var excludeTags by remember(entryKey) {
        mutableStateOf(existing?.excludeTags.orEmpty())
    }
    var catsMask by remember(entryKey) { mutableStateOf(existing?.cats ?: 0) }

    var includeInput by remember(entryKey) { mutableStateOf("") }
    var excludeInput by remember(entryKey) { mutableStateOf("") }

    fun persist(next: List<HomeEntry>) {
        AppSettings.homeEntries = next
    }

    fun save() {
        val name = label.trim().ifEmpty {
            if (isGroup) "分组 ${entries.count { it.isGroup } + 1}" else existing?.label.orEmpty()
        }
        if (isGroup && isNew && entries.count { it.isGroup } >= MAX_CUSTOM_GROUPS) {
            showSystemToast("自定义分组最多 $MAX_CUSTOM_GROUPS 个")
            return
        }
        val entry = if (isNew || isGroup) {
            HomeEntry.group(
                id = existing?.id ?: newGroupId(),
                label = name,
                includeTags = includeTags,
                excludeTags = excludeTags,
                cats = catsMask.takeIf { it != 0 },
            )
        } else {
            // 内置分类：分类位不可改，只改显示名。
            HomeEntry.cat(existing!!.mask, name)
        }
        val next = if (isNew) {
            entries + entry
        } else {
            entries.toMutableList().also { it[index] = entry }
        }
        persist(next)
        showSystemToast(if (isNew) "已添加分组：$name" else "已保存：$name")
        onBack()
    }

    fun delete() {
        if (isNew || index < 0) return
        persist(entries.filterIndexed { i, _ -> i != index })
        showSystemToast("已删除：${existing?.label.orEmpty()}")
        onBack()
    }

    fun move(delta: Int) {
        if (isNew || index < 0) return
        val target = index + delta
        if (target !in entries.indices) return
        val next = entries.toMutableList()
        val moved = next.removeAt(index)
        next.add(target, moved)
        persist(next)
        // 位置变了，key 不变，但 index 会变；直接返回列表是最直观的反馈。
        onBack()
    }

    ErosPageScaffold(
        title = if (isNew) "新建分组" else "编辑分类",
        onBack = onBack,
        actions = {
            Text(
                "保存",
                style = MiuixTheme.textStyles.button,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { save() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
    ) { innerPadding, _, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextField(
                value = label,
                onValueChange = { label = it },
                label = "名称（显示在首页分类行）",
            )

            if (isGroup) {
                TagSection(
                    title = "包含标签",
                    hint = "支持站点标签写法，如 female:big breasts；多条之间是「同时满足」",
                    tags = includeTags,
                    input = includeInput,
                    onInputChange = { includeInput = it },
                    onAdd = { tag ->
                        val clean = tag.trim()
                        if (clean.isNotEmpty() && clean !in includeTags) {
                            includeTags = includeTags + clean
                        }
                        includeInput = ""
                    },
                    onRemove = { tag -> includeTags = includeTags - tag },
                )

                TagSection(
                    title = "排除标签",
                    hint = "带这些标签的画廊不会出现（站点按 -标签 处理）",
                    tags = excludeTags,
                    input = excludeInput,
                    onInputChange = { excludeInput = it },
                    onAdd = { tag ->
                        val clean = tag.trim().removePrefix("-")
                        if (clean.isNotEmpty() && clean !in excludeTags) {
                            excludeTags = excludeTags + clean
                        }
                        excludeInput = ""
                    },
                    onRemove = { tag -> excludeTags = excludeTags - tag },
                )

                CategorySection(
                    selected = catsMask,
                    onToggle = { mask ->
                        catsMask = if (catsMask and mask != 0) catsMask and mask.inv() else catsMask or mask
                    },
                )
            }

            if (!isNew) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActionText("上移", enabled = index > 0) { move(-1) }
                    ActionText("下移", enabled = index >= 0 && index < entries.lastIndex) { move(1) }
                    Box(Modifier.weight(1f))
                    ActionText("删除", danger = true) { delete() }
                }
            }
        }
    }
}

@Composable
private fun ActionText(
    text: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text,
        style = MiuixTheme.textStyles.footnote1,
        color = when {
            !enabled -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            danger -> MiuixTheme.colorScheme.error
            else -> MiuixTheme.colorScheme.primary
        },
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 一个标签编辑区：已选 chips + 输入框 + 本地标签联想。 */
@Composable
private fun TagSection(
    title: String,
    hint: String,
    tags: List<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MiuixTheme.textStyles.body1)
        Text(
            hint,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .squircleSurface(
                                color = MiuixTheme.colorScheme.surfaceVariant,
                                cornerRadius = 10.dp,
                            )
                            .clickable { onRemove(tag) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "$tag  ✕",
                            style = MiuixTheme.textStyles.footnote2,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    label = "输入标签后点「添加」",
                )
            }
            Text(
                "添加",
                style = MiuixTheme.textStyles.button,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onAdd(input) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        // 本地标签联想：来自库里已有标签（详情页/富化时写入），前缀匹配、最多 8 条。
        val suggestions = rememberSuggestions(input)
        if (suggestions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { suggestion ->
                    Box(
                        modifier = Modifier
                            .squircleSurface(
                                color = MiuixTheme.colorScheme.surface,
                                cornerRadius = 10.dp,
                            )
                            .clickable { onAdd(suggestion) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            suggestion,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/** 输入前缀 → 本地标签库前缀匹配（去抖 200ms，避免每敲一个字都查库）。 */
@Composable
private fun rememberSuggestions(input: String): List<String> {
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(input) {
        if (input.isBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(200)
        suggestions = runCatching {
            GalleryCache.searchTags(input.trim(), 8).map { it.full }
        }.getOrDefault(emptyList())
    }
    return suggestions
}

/** 类别限定：不选任何一项 = 不限定。 */
@Composable
private fun CategorySection(selected: Int, onToggle: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("限定类别", style = MiuixTheme.textStyles.body1)
        Text(
            "不选则不限；选中后只保留这些类别",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HOME_CATEGORY_CHOICES.forEach { choice ->
                val on = selected and choice.mask != 0
                Box(
                    modifier = Modifier
                        .squircleSurface(
                            color = if (on) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surfaceVariant,
                            cornerRadius = 12.dp,
                        )
                        .clickable { onToggle(choice.mask) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        choice.label,
                        style = MiuixTheme.textStyles.footnote2,
                        color = if (on) MiuixTheme.colorScheme.onPrimary
                        else MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** 时间戳 + 随机数做 id 足够（同一个进程内不会并发生成两个）。 */
private fun newGroupId(): String =
    "g" + currentTimeMillis().toString(36) + kotlin.random.Random.nextInt(100, 999)
