// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.tags

/** 标签翻译数据库的磁盘缓存 + 加载入口。 */
expect fun saveTagDatabase(json: String)

expect fun loadCachedTagDatabase(): String?

/** 从缓存载入，返回条目数（0 = 没有缓存）。 */
expect fun initTagDatabase(): Int
