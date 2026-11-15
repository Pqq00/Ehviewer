// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.tags

import app.eros.shared.settings.AndroidPlatform
import com.hippo.ehviewer.client.EhTagDatabase
import java.io.File

/**
 * 标签库的磁盘存取。
 *
 * 源文件是社区维护的 `db.text.json`（约 4.3MB、8.8 万条），整体解析一次要 ~500ms。
 * 冷启动时卡片往往已经画出来了、翻译还没就绪，于是出现「先英文、再变中文」的闪动。
 *
 * 这里额外维护一份**预解析索引** `tagdb.index`：
 *
 * ```
 * #v1 4289376:1762500000000
 * language:chinese\t汉语
 * female:big breasts\t巨乳
 * ...
 * ```
 *
 * 首行是源文件的指纹（长度:修改时间），指纹不匹配就让索引作废、回落到 JSON 解析并重建。
 * 读索引只是逐行 split，没有 JSON 解析，实测比整体解析快 3~6 倍。
 */
private const val INDEX_VERSION = "v1"
private const val JSON_NAME = "tagdb.json"
private const val INDEX_NAME = "tagdb.index"

private fun jsonFile(): File = File(AndroidPlatform.appContext.filesDir, JSON_NAME)

private fun indexFile(): File = File(AndroidPlatform.appContext.filesDir, INDEX_NAME)

/** 源文件指纹：长度 + 最后修改时间。 */
private fun jsonStamp(): String {
    val file = jsonFile()
    return "${file.length()}:${file.lastModified()}"
}

actual fun saveTagDatabase(json: String) {
    runCatching {
        jsonFile().writeText(json)
        // 源文件换了，旧索引立刻作废，下次载入时按新内容重建。
        indexFile().delete()
    }
}

actual fun loadCachedTagDatabase(): String? = runCatching {
    val file = jsonFile()
    if (file.exists() && file.length() > 0) file.readText() else null
}.getOrNull()

actual fun initTagDatabase(): Int {
    val json = loadCachedTagDatabase() ?: return 0
    val stamp = jsonStamp()
    readIndex(stamp)?.let { return EhTagDatabase.loadEntries(it) }
    val count = EhTagDatabase.load(json)
    if (count > 0) writeIndex(stamp)
    return count
}

/** 读预解析索引；指纹不匹配、文件损坏都返回 null，由调用方回落到 JSON 解析。 */
private fun readIndex(stamp: String): Map<String, String>? {
    val file = indexFile()
    if (!file.exists() || file.length() == 0L) return null
    return try {
        file.bufferedReader().use { reader ->
            if (reader.readLine() != "$INDEX_VERSION $stamp") return null
            val entries = HashMap<String, String>(1 shl 17)
            while (true) {
                val line = reader.readLine() ?: break
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                entries[line.substring(0, tab)] = line.substring(tab + 1)
            }
            if (entries.isEmpty()) null else entries
        }
    } catch (t: Throwable) {
        null
    }
}

/** 把当前词库写成索引；先写临时文件再改名，避免半截文件被当成有效索引。 */
private fun writeIndex(stamp: String) {
    val entries = EhTagDatabase.snapshot()
    if (entries.isEmpty()) return
    runCatching {
        val tmp = File(AndroidPlatform.appContext.filesDir, "$INDEX_NAME.tmp")
        tmp.bufferedWriter().use { writer ->
            writer.write("$INDEX_VERSION $stamp")
            writer.newLine()
            entries.forEach { (key, value) ->
                writer.write(key)
                writer.write("\t")
                writer.write(value)
                writer.newLine()
            }
        }
        if (!tmp.renameTo(indexFile())) {
            indexFile().delete()
            tmp.renameTo(indexFile())
        }
    }
}
