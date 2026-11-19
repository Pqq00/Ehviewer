// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data.cache

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.eros.shared.settings.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

actual fun createGalleryCacheStore(): GalleryCacheStore =
    AndroidGalleryCacheStore(AndroidPlatform.appContext)

/**
 * P0 本地 SQLite 缓存。表：
 *  - gallery(gid PK, 元数据, tags JSON, updated_at)
 *  - list_page(key, page_index, gids JSON, next_href, fetched_at)
 *  - detail(gid PK, token, payload JSON, fetched_at)
 */
internal class AndroidGalleryCacheStore(context: Context) : GalleryCacheStore {

    private val appContext = context.applicationContext
    private val helper = Helper(appContext)
    private val mutex = Mutex()

    override suspend fun usageBytes(): Long = withContext(Dispatchers.IO) {
        // 直接把落盘数据库文件大小当作「解析模型缓存」占用：简单、和文件系统口径一致，
        // 也包含历史/标签等同一库内的表，设置页文案按「列表与详情缓存」表述。
        runCatching { appContext.getDatabasePath("gallery_cache.db").length() }.getOrDefault(0L)
    }

    override suspend fun loadPage(key: String, index: Int, maxAgeMs: Long): CachedPage? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val db = helper.readableDatabase
                val pageRow = db.rawQuery(
                    "SELECT gids, next_href, fetched_at FROM list_page WHERE key = ? AND page_index = ?",
                    arrayOf(key, index.toString()),
                ).use { c ->
                    if (!c.moveToFirst()) return@withLock null
                    Triple(c.getString(0), if (c.isNull(1)) null else c.getString(1), c.getLong(2))
                }
                val (gidsText, nextHref, fetchedAt) = pageRow
                if (System.currentTimeMillis() - fetchedAt > maxAgeMs) return@withLock null
                val gids = runCatching {
                    json.decodeFromString(ListSerializer(Long.serializer()), gidsText)
                }.getOrDefault(emptyList())
                if (gids.isEmpty()) return@withLock null
                val records = queryGalleries(db, gids)
                if (records.isEmpty()) null else CachedPage(records, nextHref, fetchedAt)
            }
        }

    override suspend fun savePage(key: String, index: Int, page: CachedPage) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val db = helper.writableDatabase
                db.beginTransaction()
                try {
                    val oldGids = db.rawQuery(
                        "SELECT gids FROM list_page WHERE key = ? AND page_index = ?",
                        arrayOf(key, index.toString()),
                    ).use { c ->
                        if (c.moveToFirst()) {
                            runCatching {
                                json.decodeFromString(ListSerializer(Long.serializer()), c.getString(0))
                            }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }
                    }
                    val oldRecords = if (oldGids.isEmpty()) emptyList() else queryGalleries(db, oldGids)
                    val merged = LinkedHashMap<Long, GalleryRecord>()
                    page.items.forEach { merged[it.gid] = it }
                    oldRecords.forEach { merged.putIfAbsent(it.gid, it) }
                    val items = merged.values.toList()
                    items.forEach { upsertGallery(db, it) }
                    val values = ContentValues().apply {
                        put("key", key)
                        put("page_index", index)
                        put(
                            "gids",
                            json.encodeToString(
                                ListSerializer(Long.serializer()),
                                items.map { it.gid },
                            ),
                        )
                        put("next_href", page.nextHref)
                        put("fetched_at", page.fetchedAt)
                    }
                    db.insertWithOnConflict("list_page", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

    override suspend fun upsertGalleries(records: List<GalleryRecord>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = helper.writableDatabase
            records.forEach { upsertGallery(db, it) }
            Unit
        }
    }

    override suspend fun loadDetail(gid: Long, token: String, maxAgeMs: Long): DetailRecord? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val db = helper.readableDatabase
                db.rawQuery(
                    "SELECT payload, fetched_at FROM detail WHERE gid = ? AND token = ?",
                    arrayOf(gid.toString(), token),
                ).use { c ->
                    if (!c.moveToFirst()) return@withLock null
                    val payload = c.getString(0)
                    val fetchedAt = c.getLong(1)
                    if (System.currentTimeMillis() - fetchedAt > maxAgeMs) return@withLock null
                    GalleryCache.decodeDetail(payload)
                }
            }
        }

    override suspend fun saveDetail(record: DetailRecord) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = helper.writableDatabase
            val values = ContentValues().apply {
                put("gid", record.gid)
                put("token", record.token)
                put("payload", GalleryCache.encodeDetail(record))
                put("fetched_at", record.fetchedAt)
            }
            db.insertWithOnConflict("detail", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            Unit
        }
    }

    override suspend fun recordHistory(gid: Long, token: String, page: Int, pages: Int) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val db = helper.writableDatabase
                val values = ContentValues().apply {
                    put("gid", gid)
                    put("token", token)
                    put("page", page)
                    put("pages", pages)
                    put("updated_at", System.currentTimeMillis())
                }
                db.insertWithOnConflict("history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                Unit
            }
        }

    override suspend fun recordHistoryVisit(gid: Long, token: String, pages: Int) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val db = helper.writableDatabase
                val now = System.currentTimeMillis()
                val values = ContentValues().apply {
                    put("pages", pages)
                    put("updated_at", now)
                }
                val updated = db.update("history", values, "gid = ?", arrayOf(gid.toString()))
                if (updated == 0) {
                    val insert = ContentValues().apply {
                        put("gid", gid)
                        put("token", token)
                        put("page", 0)
                        put("pages", pages)
                        put("updated_at", now)
                    }
                    db.insertWithOnConflict("history", null, insert, SQLiteDatabase.CONFLICT_IGNORE)
                }
                Unit
            }
        }

    override suspend fun loadHistory(limit: Int): List<HistoryEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = helper.readableDatabase
            val out = ArrayList<HistoryEntry>()
            db.rawQuery(
                "SELECT h.gid, h.token, h.page, h.pages, h.updated_at, g.title, g.thumb, g.title_jpn " +
                    "FROM history h LEFT JOIN gallery g ON g.gid = h.gid " +
                    "ORDER BY h.updated_at DESC LIMIT ?",
                arrayOf(limit.toString()),
            ).use { c ->
                while (c.moveToNext()) {
                    out += HistoryEntry(
                        gid = c.getLong(0),
                        token = c.getString(1),
                        page = c.getInt(2),
                        pages = c.getInt(3),
                        updatedAt = c.getLong(4),
                        title = if (c.isNull(5)) null else c.getString(5),
                        thumb = if (c.isNull(6)) null else c.getString(6),
                        titleJpn = if (c.isNull(7)) null else c.getString(7),
                    )
                }
            }
            out
        }
    }

    override suspend fun deleteHistory(gid: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            helper.writableDatabase.delete("history", "gid = ?", arrayOf(gid.toString()))
            Unit
        }
    }

    override suspend fun recordSearch(query: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = helper.writableDatabase
            val values = ContentValues().apply {
                put("query", query)
                put("last_used_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("search_history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.execSQL(
                "DELETE FROM search_history WHERE query NOT IN " +
                    "(SELECT query FROM search_history ORDER BY last_used_at DESC LIMIT 50)",
            )
            Unit
        }
    }

    override suspend fun loadSearchHistory(limit: Int): List<SearchHistoryEntry> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val db = helper.readableDatabase
                val out = ArrayList<SearchHistoryEntry>()
                db.rawQuery(
                    "SELECT query, last_used_at FROM search_history ORDER BY last_used_at DESC LIMIT ?",
                    arrayOf(limit.toString()),
                ).use { c ->
                    while (c.moveToNext()) {
                        out += SearchHistoryEntry(c.getString(0), c.getLong(1))
                    }
                }
                out
            }
        }

    override suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        mutex.withLock {
            helper.writableDatabase.delete("search_history", null, null)
            Unit
        }
    }

    override suspend fun upsertTags(tags: List<TagRecord>) = withContext(Dispatchers.IO) {
        if (tags.isEmpty()) return@withContext
        mutex.withLock {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                tags.forEach { t ->
                    val now = if (t.updatedAt > 0) t.updatedAt else System.currentTimeMillis()
                    // 已被词频填充的行保留 count；translated 为空时不要覆盖旧翻译。
                    val update = ContentValues().apply {
                        put("namespace", t.namespace)
                        put("name", t.name)
                        if (t.translated != null) put("translated", t.translated)
                        put("updated_at", now)
                    }
                    val updated = db.update("tag", update, "full = ?", arrayOf(t.full))
                    if (updated == 0) {
                        val insert = ContentValues().apply {
                            put("full", t.full)
                            put("namespace", t.namespace)
                            put("name", t.name)
                            put("translated", t.translated)
                            put("count", t.count)
                            put("updated_at", now)
                        }
                        db.insertWithOnConflict("tag", null, insert, SQLiteDatabase.CONFLICT_IGNORE)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            Unit
        }
    }

    override suspend fun saveTagCounts(rows: List<TagCountRow>) = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext
        mutex.withLock {
            val db = helper.writableDatabase
            val now = System.currentTimeMillis()
            db.beginTransaction()
            try {
                rows.forEach { row ->
                    val countValues = ContentValues().apply {
                        put("count", row.count)
                        put("updated_at", now)
                    }
                    val updated = db.update("tag", countValues, "full = ?", arrayOf(row.full))
                    if (updated == 0) {
                        val insert = ContentValues().apply {
                            put("full", row.full)
                            put("namespace", row.namespace)
                            put("name", row.name)
                            put("translated", null as String?)
                            put("count", row.count)
                            put("updated_at", now)
                        }
                        db.insertWithOnConflict("tag", null, insert, SQLiteDatabase.CONFLICT_IGNORE)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            Unit
        }
    }

    override suspend fun tagCountMeta(): TagCountMeta? = withContext(Dispatchers.IO) {
        mutex.withLock {
            helper.readableDatabase.rawQuery(
                "SELECT version, updated_at FROM tag_count_meta WHERE id = 1",
                null,
            ).use { c ->
                if (c.moveToNext()) TagCountMeta(c.getString(0), c.getLong(1)) else null
            }
        }
    }

    override suspend fun saveTagCountMeta(meta: TagCountMeta) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val values = ContentValues().apply {
                put("id", 1)
                put("version", meta.version)
                put("updated_at", meta.updatedAt)
            }
            helper.writableDatabase.insertWithOnConflict(
                "tag_count_meta", null, values, SQLiteDatabase.CONFLICT_REPLACE,
            )
            Unit
        }
    }

    override suspend fun searchTags(prefix: String, limit: Int): List<TagRecord> =
        withContext(Dispatchers.IO) {
            val trimmed = prefix.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val escaped = trimmed
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            mutex.withLock {
                val out = ArrayList<TagRecord>(limit)
                helper.readableDatabase.rawQuery(
                    "SELECT full, namespace, name, translated, count, updated_at FROM tag " +
                        "WHERE full LIKE ? ESCAPE '\\' ORDER BY count DESC, namespace ASC, name ASC LIMIT ?",
                    arrayOf(escaped + "%", limit.toString()),
                ).use { c ->
                    while (c.moveToNext()) {
                        out += TagRecord(
                            full = c.getString(0),
                            namespace = c.getString(1),
                            name = c.getString(2),
                            translated = if (c.isNull(3)) null else c.getString(3),
                            count = c.getInt(4),
                            updatedAt = c.getLong(5),
                        )
                    }
                }
                out
            }
        }

    override suspend fun loadTags(fulls: List<String>): Map<String, TagRecord> =
        withContext(Dispatchers.IO) {
            if (fulls.isEmpty()) return@withContext emptyMap()
            mutex.withLock {
                val db = helper.readableDatabase
                val out = LinkedHashMap<String, TagRecord>()
                val placeholders = fulls.joinToString(",") { "?" }
                db.rawQuery(
                    "SELECT full, namespace, name, translated, count, updated_at FROM tag WHERE full IN ($placeholders)",
                    fulls.toTypedArray(),
                ).use { c ->
                    while (c.moveToNext()) {
                        val rec = TagRecord(
                            full = c.getString(0),
                            namespace = c.getString(1),
                            name = c.getString(2),
                            translated = if (c.isNull(3)) null else c.getString(3),
                            count = c.getInt(4),
                            updatedAt = c.getLong(5),
                        )
                        out[rec.full] = rec
                    }
                }
                out
            }
        }

    override suspend fun prune(now: Long, pageMaxAgeMs: Long, detailMaxAgeMs: Long) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val db = helper.writableDatabase
                db.delete("list_page", "fetched_at < ?", arrayOf((now - pageMaxAgeMs).toString()))
                db.delete("detail", "fetched_at < ?", arrayOf((now - detailMaxAgeMs).toString()))
                // 画廊元数据保留 30 天；列表/详情引用会优先命中。
                db.delete("gallery", "updated_at < ?", arrayOf((now - 30L * 24 * 60 * 60 * 1000).toString()))
            }
            Unit
        }

    override suspend fun clearListPages() = withContext(Dispatchers.IO) {
        mutex.withLock {
            helper.writableDatabase.delete("list_page", null, null)
            Unit
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = helper.writableDatabase
            db.delete("list_page", null, null)
            db.delete("detail", null, null)
            db.delete("gallery", null, null)
            Unit
        }
    }

    private fun queryGalleries(db: SQLiteDatabase, gids: List<Long>): List<GalleryRecord> {
        if (gids.isEmpty()) return emptyList()
        val placeholders = gids.joinToString(",") { "?" }
        val byGid = LinkedHashMap<Long, GalleryRecord>()
        db.rawQuery(
            "SELECT gid, token, title, thumb, category, uploader, pages, rating, posted, tags, updated_at " +
                "FROM gallery WHERE gid IN ($placeholders)",
            gids.map { it.toString() }.toTypedArray(),
        ).use { c ->
            while (c.moveToNext()) {
                val record = GalleryRecord(
                    gid = c.getLong(0),
                    token = c.getString(1),
                    title = c.getString(2),
                    thumb = if (c.isNull(3)) null else c.getString(3),
                    category = c.getInt(4),
                    uploader = if (c.isNull(5)) null else c.getString(5),
                    pages = c.getInt(6),
                    rating = c.getFloat(7),
                    posted = if (c.isNull(8)) null else c.getString(8),
                    tags = runCatching {
                        json.decodeFromString(ListSerializer(String.serializer()), c.getString(9))
                    }.getOrDefault(emptyList()),
                    updatedAt = c.getLong(10),
                )
                byGid[record.gid] = record
            }
        }
        return gids.mapNotNull { byGid[it] }
    }

    private fun upsertGallery(db: SQLiteDatabase, g: GalleryRecord) {
        val values = ContentValues().apply {
            put("gid", g.gid)
            put("token", g.token)
            put("title", g.title)
            put("title_jpn", g.titleJpn)
            put("thumb", g.thumb)
            put("category", g.category)
            put("uploader", g.uploader)
            put("pages", g.pages)
            put("rating", g.rating)
            put("posted", g.posted)
            put("tags", json.encodeToString(ListSerializer(String.serializer()), g.tags))
            put("updated_at", g.updatedAt)
        }
        db.insertWithOnConflict("gallery", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override suspend fun loadImageUrl(gid: Long, index: Int): ImageUrlRecord? =
        withContext(Dispatchers.IO) {
            helper.readableDatabase.rawQuery(
                "SELECT page_url,image_url,fetched_at FROM image_url WHERE gid=? AND page_index=?",
                arrayOf(gid.toString(), index.toString()),
            ).use { c ->
                if (c.moveToFirst()) {
                    ImageUrlRecord(gid, index, c.getString(0), c.getString(1), c.getLong(2))
                } else {
                    null
                }
            }
        }

    override suspend fun saveImageUrl(record: ImageUrlRecord) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("gid", record.gid)
            put("page_index", record.index)
            put("page_url", record.pageUrl)
            put("image_url", record.imageUrl)
            put("fetched_at", if (record.fetchedAt > 0) record.fetchedAt else System.currentTimeMillis())
        }
        helper.writableDatabase.insertWithOnConflict(
            "image_url",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        Unit
    }

    override suspend fun deleteImageUrl(gid: Long, index: Int) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(
            "image_url",
            "gid = ? AND page_index = ?",
            arrayOf(gid.toString(), index.toString()),
        )
        Unit
    }

    override suspend fun loadMeta(gids: List<Long>): Map<Long, GalleryMetaRecord> =
        withContext(Dispatchers.IO) {
            if (gids.isEmpty()) return@withContext emptyMap()
            val out = HashMap<Long, GalleryMetaRecord>(gids.size * 2)
            gids.chunked(200).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                helper.readableDatabase.rawQuery(
                    "SELECT gid,tags,rating,pages,posted,fetched_at FROM gallery_meta WHERE gid IN ($placeholders)",
                    chunk.map { it.toString() }.toTypedArray(),
                ).use { c ->
                    while (c.moveToNext()) {
                        val gid = c.getLong(0)
                        out[gid] = GalleryMetaRecord(
                            gid = gid,
                            tags = runCatching {
                                json.decodeFromString(ListSerializer(String.serializer()), c.getString(1))
                            }.getOrDefault(emptyList()),
                            rating = c.getDouble(2).toFloat(),
                            pages = c.getInt(3),
                            posted = c.getString(4),
                            fetchedAt = c.getLong(5),
                        )
                    }
                }
            }
            out
        }

    override suspend fun saveMeta(records: List<GalleryMetaRecord>) = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            records.forEach { r ->
                val values = ContentValues().apply {
                    put("gid", r.gid)
                    put("tags", json.encodeToString(ListSerializer(String.serializer()), r.tags))
                    put("rating", r.rating)
                    put("pages", r.pages)
                    put("posted", r.posted)
                    put("fetched_at", if (r.fetchedAt > 0) r.fetchedAt else System.currentTimeMillis())
                }
                db.insertWithOnConflict("gallery_meta", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private class Helper(context: Context) :
        SQLiteOpenHelper(context, "gallery_cache.db", null, 6) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE gallery(
                  gid INTEGER PRIMARY KEY,
                  token TEXT NOT NULL,
                  title TEXT NOT NULL,
                  title_jpn TEXT,
                  thumb TEXT,
                  category INTEGER NOT NULL DEFAULT 0,
                  uploader TEXT,
                  pages INTEGER NOT NULL DEFAULT 0,
                  rating REAL NOT NULL DEFAULT -1,
                  posted TEXT,
                  tags TEXT NOT NULL DEFAULT '[]',
                  updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE list_page(
                  key TEXT NOT NULL,
                  page_index INTEGER NOT NULL,
                  gids TEXT NOT NULL,
                  next_href TEXT,
                  fetched_at INTEGER NOT NULL,
                  PRIMARY KEY(key, page_index)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX idx_list_page_fetched ON list_page(fetched_at)")
            db.execSQL(
                """
                CREATE TABLE detail(
                  gid INTEGER PRIMARY KEY,
                  token TEXT NOT NULL,
                  payload TEXT NOT NULL,
                  fetched_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX idx_detail_fetched ON detail(fetched_at)")
            createExtraTables(db)
        }

        private fun createExtraTables(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS history(
                  gid INTEGER PRIMARY KEY,
                  token TEXT NOT NULL,
                  page INTEGER NOT NULL DEFAULT 0,
                  pages INTEGER NOT NULL DEFAULT 0,
                  updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_updated ON history(updated_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS search_history(
                  query TEXT PRIMARY KEY,
                  last_used_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tag(
                  full TEXT PRIMARY KEY,
                  namespace TEXT NOT NULL,
                  name TEXT NOT NULL,
                  translated TEXT,
                  count INTEGER NOT NULL DEFAULT 0,
                  updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS image_url(
                  gid INTEGER NOT NULL,
                  page_index INTEGER NOT NULL,
                  page_url TEXT NOT NULL,
                  image_url TEXT NOT NULL,
                  fetched_at INTEGER NOT NULL,
                  PRIMARY KEY(gid, page_index)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tag_count_meta(
                  id INTEGER PRIMARY KEY,
                  version TEXT NOT NULL,
                  updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS gallery_meta(
                  gid INTEGER PRIMARY KEY,
                  tags TEXT NOT NULL,
                  rating REAL NOT NULL,
                  pages INTEGER NOT NULL,
                  posted TEXT,
                  fetched_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) createExtraTables(db)
            if (oldVersion < 3) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS tag_count_meta(id INTEGER PRIMARY KEY, version TEXT NOT NULL, updated_at INTEGER NOT NULL)",
                )
            }
            if (oldVersion < 4) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS image_url(gid INTEGER NOT NULL, page_index INTEGER NOT NULL, page_url TEXT NOT NULL, image_url TEXT NOT NULL, fetched_at INTEGER NOT NULL, PRIMARY KEY(gid, page_index))",
                )
            }
            if (oldVersion < 5) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS gallery_meta(gid INTEGER PRIMARY KEY, tags TEXT NOT NULL, rating REAL NOT NULL, pages INTEGER NOT NULL, posted TEXT, fetched_at INTEGER NOT NULL)",
                )
            }
            if (oldVersion < 6) {
                db.execSQL("ALTER TABLE gallery ADD COLUMN title_jpn TEXT")
            }
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
