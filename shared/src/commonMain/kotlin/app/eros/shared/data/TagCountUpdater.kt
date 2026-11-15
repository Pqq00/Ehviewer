// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

/** P1-b：标签词频数据的一次更新结果。 */
data class TagCountUpdate(
    val version: String,
    val count: Int,
    val skipped: Boolean,
)

/**
 * 下载并解析 `tagname_count.csv.gz`，写入本地 `tag` 表的 `count` 列。
 *
 * @param force true 时忽略版本比较，强制重新下载。
 * @return null 表示失败（网络/解析），调用方保持旧数据。
 */
expect suspend fun updateTagCounts(force: Boolean = false): TagCountUpdate?
