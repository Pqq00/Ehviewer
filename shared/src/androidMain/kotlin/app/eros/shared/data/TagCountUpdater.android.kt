// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

import app.eros.shared.currentTimeMillis
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.data.cache.TagCountMeta
import app.eros.shared.data.cache.TagCountRow
import com.hippo.ehviewer.net.EhHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.zip.GZIPInputStream

private const val TAG_COUNT_URL =
    "https://github.com/mokurin000/e-hentai-tag-count/releases/latest/download/tagname_count.csv.gz"

actual suspend fun updateTagCounts(force: Boolean): TagCountUpdate? = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder()
            .url(TAG_COUNT_URL)
            .header("Accept", "application/octet-stream")
            .build()
        EhHttp.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val finalUrl = response.request.url.toString()
            val version = Regex("/download/([^/]+)/").find(finalUrl)?.groupValues?.get(1)
                ?: response.header("ETag")?.trim('"')
                ?: response.header("Last-Modified")
                ?: ""
            val meta = GalleryCache.tagCountMeta()
            if (!force && version.isNotEmpty() && meta?.version == version) {
                return@withContext TagCountUpdate(version, 0, true)
            }
            val raw = response.body?.bytes() ?: return@withContext null
            val text = GZIPInputStream(raw.inputStream())
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val rows = ArrayList<TagCountRow>(100_000)
            text.lineSequence().drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val comma = line.lastIndexOf(',')
                if (comma <= 0) return@forEach
                val key = line.substring(0, comma).trim().trim('"')
                val count = line.substring(comma + 1).trim().toIntOrNull() ?: return@forEach
                if (count < 5 || !key.contains(':')) return@forEach
                rows += TagCountRow(
                    full = key,
                    namespace = key.substringBefore(':'),
                    name = key.substringAfter(':'),
                    count = count,
                )
            }
            rows.chunked(500).forEach { GalleryCache.saveTagCounts(it) }
            GalleryCache.saveTagCountMeta(TagCountMeta(version, currentTimeMillis()))
            println("[TagCount] updated version=" + version + " rows=" + rows.size)
            TagCountUpdate(version, rows.size, false)
        }
    }.getOrNull()
}
