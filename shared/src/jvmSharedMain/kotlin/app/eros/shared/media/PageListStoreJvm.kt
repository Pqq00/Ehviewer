// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.media

import com.hippo.ehviewer.util.AppLog
import app.eros.shared.platform.StorageArea
import app.eros.shared.platform.storagePath
import java.io.File

private const val MAX_GALLERIES = 100

private fun pageListDir(): File {
    val dir = File(storagePath(StorageArea.PageLists))
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun pageListFile(gid: Long): File = File(pageListDir(), "$gid.txt")

actual fun readPageList(gid: Long, maxAgeMs: Long): PageListCache? = runCatching {
    val file = pageListFile(gid)
    if (!file.exists()) return@runCatching null
    val lines = file.readLines()
    if (lines.size < 4 || lines[0] != "v1") return@runCatching null
    val savedAt = lines[1].toLongOrNull() ?: return@runCatching null
    if (maxAgeMs != Long.MAX_VALUE && System.currentTimeMillis() - savedAt > maxAgeMs) {
        AppLog.d("PageListStore", "expired gid=$gid ageMs=${System.currentTimeMillis() - savedAt}")
        return@runCatching null
    }
    val totalPages = lines[2].toIntOrNull() ?: return@runCatching null
    val urls = lines.drop(3).filter { it.isNotBlank() }
    if (urls.isEmpty()) null else PageListCache(savedAt, totalPages, urls)
}.getOrNull()

actual fun writePageList(gid: Long, totalPages: Int, urls: List<String>) {
    runCatching {
        val file = pageListFile(gid)
        val tmp = File(pageListDir(), "$gid.txt.tmp")
        tmp.writeText(
            buildString {
                appendLine("v1")
                appendLine(System.currentTimeMillis())
                appendLine(totalPages)
                urls.forEach { appendLine(it) }
            },
        )
        if (file.exists()) file.delete()
        tmp.renameTo(file)
        trimOld()
    }
}

actual fun clearPageLists() {
    runCatching { pageListDir().deleteRecursively() }
}

actual fun pageListCacheSize(): Long = runCatching {
    pageListDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

/** 只保留最近 [MAX_GALLERIES] 个画廊的列表（按最后修改时间）。 */
private fun trimOld() {
    runCatching {
        val files = pageListDir().listFiles()?.filter { it.isFile && it.name.endsWith(".txt") } ?: return
        if (files.size <= MAX_GALLERIES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_GALLERIES)
            .forEach { it.delete() }
    }
}
