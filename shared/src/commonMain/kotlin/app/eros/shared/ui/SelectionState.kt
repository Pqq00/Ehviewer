// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 列表多选状态：选择集合 + 「是否处于多选」。
 *
 * 下载页与浏览历史页共用，避免各写一份 `selecting` / `selected` / 全选判断后
 * 细节慢慢分叉（例如「全选」的空列表语义、取消到最后一条后是否退出多选）。
 */
@Stable
internal class SelectionState<K> {
    var selecting by mutableStateOf(false)
        private set

    private val entries = mutableStateMapOf<K, Boolean>()

    val size: Int get() = entries.size

    fun contains(key: K): Boolean = entries.containsKey(key)

    fun isNotEmpty(): Boolean = entries.isNotEmpty()

    /** 进入多选并选中 [key]（长按进入多选）。 */
    fun start(key: K) {
        selecting = true
        entries[key] = true
    }

    /** 切换单条选中状态。 */
    fun toggle(key: K) {
        if (entries.remove(key) == null) entries[key] = true
    }

    fun allSelected(keys: Collection<K>): Boolean =
        keys.isNotEmpty() && keys.all { entries.containsKey(it) }

    /** 当前选中的 key 快照，用于批量操作（删除等）。 */
    fun selectedKeys(): Set<K> = entries.keys.toSet()

    fun toggleAll(keys: Collection<K>) {
        if (allSelected(keys)) entries.clear() else keys.forEach { entries[it] = true }
    }

    fun exit() {
        selecting = false
        entries.clear()
    }
}

@Composable
internal fun <K> rememberSelectionState(): SelectionState<K> = remember { SelectionState() }
