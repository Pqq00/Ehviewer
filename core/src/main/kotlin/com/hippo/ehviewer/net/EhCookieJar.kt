// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.util.AppLog
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Cookie 存储（对应 EhViewer 的 EhCookieStore）。
 *
 * 上游把 cookie 存进 SQLite；这里用一个内存列表 + 可导出/导入的文本，
 * 由平台层（SharedPreferences）负责落盘，避免把 Android SQLite 拖进来。
 *
 * 关键 cookie：
 *  - ipb_member_id / ipb_pass_hash：登录态
 *  - igneous：进里站（exhentai）必须
 *
 * 过期的 cookie 在每次读写前清掉：登录态过期后如果还留着旧值，
 * 请求会带着失效凭据去站点，拿回来的又是登录提示页。
 */
object EhCookieJar : CookieJar {

    const val KEY_MEMBER_ID = "ipb_member_id"
    const val KEY_PASS_HASH = "ipb_pass_hash"
    const val KEY_IGNEOUS = "igneous"

    /** 导出格式的列数（第 8 列 hostOnly 是后加的，旧数据只有 7 列）。 */
    private const val EXPORT_COLUMNS = 8
    private const val LEGACY_COLUMNS = 7

    private val cookies = mutableListOf<Cookie>()

    /** 每次 cookie 变化后回调，供平台层持久化。回调在锁外触发。 */
    @Volatile
    var onChange: ((String) -> Unit)? = null

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        // 临时 cookie 不持久化；igneous=mystery 是里站"未登录"占位。
        val filtered = cookies.filter { c ->
            c.name != "__utmp" && !(c.name == KEY_IGNEOUS && c.value == "mystery")
        }
        if (filtered.isEmpty()) return
        val snapshot = synchronized(this.cookies) {
            purgeExpiredLocked(System.currentTimeMillis())
            filtered.forEach { incoming ->
                this.cookies.removeAll {
                    it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path
                }
                this.cookies.add(incoming)
            }
            exportLocked()
        }
        // 回调里会走 SharedPreferences 写盘，不能在持有列表锁时做。
        onChange?.invoke(snapshot)
        AppLog.d("Cookie", "saved ${filtered.size} from ${url.host}: ${filtered.joinToString { it.name }}")
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
        purgeExpiredLocked(System.currentTimeMillis())
        val stored = cookies.filter { it.matches(url) }
        if (EhUrl.isEhSite(url.host)) {
            val names = stored.mapTo(HashSet()) { it.name }
            buildList {
                addAll(stored)
                // 默认带上的两个开关：关闭新手提示、显示标签数据。
                if ("nw" !in names) add(hostCookie(url.host, "nw", "1"))
                if ("datatags" !in names) add(hostCookie(url.host, "datatags", "1"))
            }
        } else {
            stored
        }
    }

    private fun hostCookie(host: String, name: String, value: String): Cookie =
        Cookie.Builder().name(name).value(value).hostOnlyDomain(host).path("/").build()

    /** 手动写入一个 cookie（Cookie 登录用）。 */
    fun put(name: String, value: String, domain: String, hostOnly: Boolean = false) {
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .path("/")
            .expiresAt(System.currentTimeMillis() + 365L * 24 * 3600 * 1000)
        if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
        val cookie = builder.build()
        val snapshot = synchronized(cookies) {
            purgeExpiredLocked(System.currentTimeMillis())
            cookies.removeAll { it.name == name && it.domain == cookie.domain && it.path == "/" }
            cookies.add(cookie)
            exportLocked()
        }
        onChange?.invoke(snapshot)
    }

    /** 是否存在**未过期**的该名字 cookie。 */
    fun has(name: String): Boolean = synchronized(cookies) {
        purgeExpiredLocked(System.currentTimeMillis())
        cookies.any { it.name == name }
    }

    /** 读取 cookie 值（读不到或已过期返回 null）。 */
    fun get(name: String): String? = synchronized(cookies) {
        purgeExpiredLocked(System.currentTimeMillis())
        cookies.firstOrNull { it.name == name }?.value
    }

    fun clear() {
        synchronized(cookies) { cookies.clear() }
        onChange?.invoke("")
    }

    /**
     * 导出为每行 `name\tvalue\tdomain\tpath\texpiresAt\tsecure\thttpOnly\thostOnly`。
     *
     * 第 8 列 hostOnly 是后加的：`forums.e-hentai.org` 这类 hostOnly cookie
     * 如果按 domain 语义读回，会被带到所有子域上。
     */
    fun export(): String = synchronized(cookies) {
        purgeExpiredLocked(System.currentTimeMillis())
        exportLocked()
    }

    private fun exportLocked(): String = cookies.joinToString("\n") { c ->
        listOf(
            c.name,
            c.value,
            c.domain,
            c.path,
            c.expiresAt.toString(),
            if (c.secure) "1" else "0",
            if (c.httpOnly) "1" else "0",
            if (c.hostOnly) "1" else "0",
        ).joinToString("\t")
    }

    fun import(data: String) {
        if (data.isBlank()) return
        val now = System.currentTimeMillis()
        val parsed = data.lineSequence().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split('\t')
            if (parts.size < LEGACY_COLUMNS) return@mapNotNull null
            runCatching {
                val expiresAt = parts[4].toLong()
                // 过期条目直接丢掉，省得下次请求带着失效凭据。
                if (expiresAt <= now) return@runCatching null
                val builder = Cookie.Builder()
                    .name(parts[0])
                    .value(parts[1])
                    .path(parts[3].ifBlank { "/" })
                    .expiresAt(expiresAt)
                // 旧数据没有 hostOnly 列：沿用过去的行为，按 domain 处理。
                if (parts.size >= EXPORT_COLUMNS && parts[7] == "1") {
                    builder.hostOnlyDomain(parts[2])
                } else {
                    builder.domain(parts[2])
                }
                if (parts[5] == "1") builder.secure()
                if (parts[6] == "1") builder.httpOnly()
                builder.build()
            }.getOrNull()
        }.toList()

        synchronized(cookies) {
            cookies.clear()
            cookies.addAll(parsed)
        }
        AppLog.i("Cookie", "imported ${parsed.size}")
    }

    /** 调用方必须持有 [cookies] 锁。 */
    private fun purgeExpiredLocked(now: Long) {
        val removed = cookies.removeAll { it.expiresAt < now }
        if (removed) AppLog.d("Cookie", "purged expired cookies")
    }
}
