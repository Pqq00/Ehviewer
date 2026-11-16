// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

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
 */
object EhCookieJar : CookieJar {

    const val KEY_MEMBER_ID = "ipb_member_id"
    const val KEY_PASS_HASH = "ipb_pass_hash"
    const val KEY_IGNEOUS = "igneous"

    private val cookies = mutableListOf<Cookie>()

    /** 每次 cookie 变化后回调，供平台层持久化。 */
    @Volatile
    var onChange: ((String) -> Unit)? = null

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        // 临时 cookie 不持久化；igneous=mystery 是里站"未登录"占位。
        val filtered = cookies.filter { c ->
            c.name != "__utmp" && !(c.name == KEY_IGNEOUS && c.value == "mystery")
        }
        if (filtered.isEmpty()) return
        synchronized(this.cookies) {
            filtered.forEach { incoming ->
                this.cookies.removeAll {
                    it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path
                }
                this.cookies.add(incoming)
            }
            onChange?.invoke(export())
        }
        AppLog.d("Cookie", "saved ${filtered.size} from ${url.host}: ${filtered.joinToString { it.name }}")
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
        val stored = cookies.filter { it.matches(url) }
        if (url.host.endsWith("e-hentai.org") || url.host.endsWith("exhentai.org")) {
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
        synchronized(cookies) {
            cookies.removeAll { it.name == name && it.domain == cookie.domain && it.path == "/" }
            cookies.add(cookie)
        }
        onChange?.invoke(export())
    }
    fun has(name: String): Boolean = synchronized(cookies) { cookies.any { it.name == name } }

    /** 读取 cookie 值（读不到返回 null）。 */
    fun get(name: String): String? = synchronized(cookies) {
        cookies.firstOrNull { it.name == name }?.value
    }

    fun clear() {
        synchronized(cookies) { cookies.clear() }
        onChange?.invoke("")
    }

    /** 导出为 `name\tvalue\tdomain\tpath\texpiresAt\tsecure\thttpOnly` 每行一条。 */
    fun export(): String = synchronized(cookies) {
        cookies.joinToString("\n") { c ->
            listOf(
                c.name,
                c.value,
                c.domain,
                c.path,
                c.expiresAt.toString(),
                if (c.secure) "1" else "0",
                if (c.httpOnly) "1" else "0",
            ).joinToString("\t")
        }
    }

    fun import(data: String) {
        if (data.isBlank()) return
        val parsed = data.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 7) return@mapNotNull null
            runCatching {
                Cookie.Builder()
                    .name(parts[0])
                    .value(parts[1])
                    .domain(parts[2])
                    .path(parts[3])
                    .expiresAt(parts[4].toLong())
                    .apply { if (parts[5] == "1") secure() }
                    .apply { if (parts[6] == "1") httpOnly() }
                    .build()
            }.getOrNull()
        }.toList()

        synchronized(cookies) {
            cookies.clear()
            cookies.addAll(parsed)
        }
        AppLog.i("Cookie", "imported ${parsed.size}")
    }
}
