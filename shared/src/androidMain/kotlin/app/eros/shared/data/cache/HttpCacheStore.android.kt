// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data.cache

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.hippo.ehviewer.net.CachedHttpResponse
import com.hippo.ehviewer.net.HttpCacheBridge

/**
 * 原始 HTTP 响应缓存（SQLite）。
 *
 * 按 URL 存 body + contentType + 过期时间，
 * 命中时由 [com.hippo.ehviewer.net.HttpCacheInterceptor] 直接返回合成响应。
 */
class AndroidHttpCacheBridge(context: Context) : HttpCacheBridge {

    private val helper = object : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS http_cache (
                    url TEXT PRIMARY KEY,
                    content_type TEXT,
                    body BLOB NOT NULL,
                    expires_at INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_http_cache_expires ON http_cache(expires_at)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    override fun get(url: String, now: Long): CachedHttpResponse? {
        val db = helper.readableDatabase
        db.query(
            "http_cache",
            arrayOf("content_type", "body", "expires_at"),
            "url = ?",
            arrayOf(url),
            null,
            null,
            null,
        ).use { c ->
            if (!c.moveToFirst()) return null
            val expiresAt = c.getLong(2)
            if (expiresAt <= now) {
                db.delete("http_cache", "url = ?", arrayOf(url))
                return null
            }
            return CachedHttpResponse(
                body = c.getBlob(1) ?: return null,
                contentType = c.getString(0),
                expiresAt = expiresAt,
            )
        }
    }

    override fun put(url: String, response: CachedHttpResponse) {
        val values = ContentValues().apply {
            put("url", url)
            put("content_type", response.contentType)
            put("body", response.body)
            put("expires_at", response.expiresAt)
            put("created_at", System.currentTimeMillis())
        }
        helper.writableDatabase.insertWithOnConflict(
            "http_cache",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        trimToSize()
    }

    /** 容量上限 64MB，超出时按 created_at 从旧到新删除。 */
    fun trimToSize(maxBytes: Long = MAX_BYTES) {
        val db = helper.writableDatabase
        repeat(8) {
            val total = db.rawQuery("SELECT IFNULL(SUM(LENGTH(body)),0) FROM http_cache", null).use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            }
            if (total <= maxBytes) return
            db.execSQL(
                "DELETE FROM http_cache WHERE url IN " +
                    "(SELECT url FROM http_cache ORDER BY created_at ASC LIMIT 64)",
            )
        }
    }

    override fun removePrefix(prefix: String) {
        helper.writableDatabase.delete("http_cache", "url LIKE ?", arrayOf("$prefix%"))
    }

    override fun sizeBytes(): Long = runCatching {
        helper.readableDatabase.rawQuery("SELECT IFNULL(SUM(LENGTH(body)),0) FROM http_cache", null).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }
    }.getOrDefault(0L)

    override fun clear() {
        helper.writableDatabase.delete("http_cache", null, null)
    }

    /** 启动时清理过期项。 */
    fun pruneExpired(now: Long = System.currentTimeMillis()) {
        helper.writableDatabase.delete("http_cache", "expires_at <= ?", arrayOf(now.toString()))
    }

    private companion object {
        const val DB_NAME = "http_cache.db"
        const val DB_VERSION = 1
        const val MAX_BYTES = 64L * 1024 * 1024
    }
}
