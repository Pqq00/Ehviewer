// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.media

import app.eros.shared.settings.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private fun pagesRoot(): File = File(AndroidPlatform.appContext.cacheDir, "pages")

private fun pageDir(gid: Long): File {
    val dir = File(pagesRoot(), gid.toString())
    if (!dir.exists()) dir.mkdirs()
    return dir
}

actual fun readPageCache(gid: Long, index: Int): ByteArray? = runCatching {
    val file = File(pageDir(gid), "$index.img")
    if (file.exists() && file.length() > 0) {
        // 触碰目录时间，参与 LRU 排序
        file.parentFile?.setLastModified(System.currentTimeMillis())
        file.readBytes()
    } else {
        null
    }
}.getOrNull()

actual fun writePageCache(gid: Long, index: Int, bytes: ByteArray) {
    runCatching {
        File(pageDir(gid), "$index.img").writeBytes(bytes)
        // 裁剪会全量扫描目录（两次 walkTopDown），不放在写入路径上：
        // 翻页停下来 3 秒后（或距上次裁剪超过 2 分钟）再在 IO 线程上跑。
        scheduleTrim()
    }
}

// ---------------------------------------------------------------- 空闲裁剪

private const val TRIM_IDLE_DELAY_MS = 3_000L
private const val TRIM_MAX_INTERVAL_MS = 120_000L

private val trimScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)

private var pendingTrim: Job? = null
private var lastTrimAt = 0L

/** 空闲时裁剪：写入停止若干秒后跑；连续翻页时最多拖到 [TRIM_MAX_INTERVAL_MS]。 */
private fun scheduleTrim() {
    val now = System.currentTimeMillis()
    if (now - lastTrimAt > TRIM_MAX_INTERVAL_MS) {
        pendingTrim?.cancel()
        pendingTrim = trimScope.launch { trimNow() }
        return
    }
    pendingTrim?.cancel()
    pendingTrim = trimScope.launch {
        delay(TRIM_IDLE_DELAY_MS)
        trimNow()
    }
}

private fun trimNow() {
    val now = System.currentTimeMillis()
    // 两次裁剪至少隔 1 秒，避免刚进入空闲就反复扫目录
    if (now - lastTrimAt < 1_000L) return
    lastTrimAt = now
    trimToLimit()
}

actual fun pageCacheSize(): Long = runCatching {
    pagesRoot().walkTopDown().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

actual fun clearPageCache() {
    runCatching { pagesRoot().deleteRecursively() }
}

/** 超出 [cacheLimitBytes()] 时，按目录最后修改时间淘汰最旧的 gid。 */
private fun trimToLimit() {
    runCatching {
        val root = pagesRoot()
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return
        var total = dirs.sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        if (total <= cacheLimitBytes()) return
        // 最久未访问的排在前面
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