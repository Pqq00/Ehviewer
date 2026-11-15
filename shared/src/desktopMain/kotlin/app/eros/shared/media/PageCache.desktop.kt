// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private fun pagesRoot(): File = File(System.getProperty("java.io.tmpdir"), "eros-pages")

private fun pageDir(gid: Long): File {
    val dir = File(pagesRoot(), gid.toString())
    if (!dir.exists()) dir.mkdirs()
    return dir
}

actual fun readPageCache(gid: Long, index: Int): ByteArray? = runCatching {
    val file = File(pageDir(gid), "$index.img")
    if (file.exists() && file.length() > 0) {
        file.parentFile?.setLastModified(System.currentTimeMillis())
        file.readBytes()
    } else {
        null
    }
}.getOrNull()

actual fun writePageCache(gid: Long, index: Int, bytes: ByteArray) {
    runCatching {
        File(pageDir(gid), "$index.img").writeBytes(bytes)
        // 与 android 一致：裁剪不在写入路径上跑，空闲 3 秒后再扫目录
        scheduleTrim()
    }
}

private val trimScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)

private var pendingTrim: Job? = null

private fun scheduleTrim() {
    pendingTrim?.cancel()
    pendingTrim = trimScope.launch {
        delay(3_000L)
        trimToLimit()
    }
}

actual fun pageCacheSize(): Long = runCatching {
    pagesRoot().walkTopDown().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

actual fun clearPageCache() {
    runCatching { pagesRoot().deleteRecursively() }
}

private fun trimToLimit() {
    runCatching {
        val root = pagesRoot()
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return
        var total = dirs.sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        if (total <= cacheLimitBytes()) return
        dirs.sortedBy { it.lastModified() }.forEach { dir ->
            if (total <= cacheLimitBytes()) return
            val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            if (dir.deleteRecursively()) total -= size
        }
    }
}
/** 上限取自设置（MB → 字节）。 */
private fun cacheLimitBytes(): Long =
    app.eros.shared.settings.AppSettings.pageCacheLimitMb.toLong() * 1024L * 1024L