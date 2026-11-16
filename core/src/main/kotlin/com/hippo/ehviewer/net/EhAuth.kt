// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.parser.SignInParser
import com.hippo.ehviewer.util.AppLog
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException

/** 里站可用性检查结果。 */
enum class ExStatus {
    OK,             // 能进里站
    SAD_PANDA,      // 登录态无效 / 无里站权限
    NOT_LOGGED_IN,  // 本地没有登录 cookie
    NETWORK_ERROR,  // 请求失败（网络/代理问题）
}

/**
 * 登录 / 退出 / 登录态检查。
 *
 * 支持两种登录方式：
 *  1. [signIn]：账号密码走论坛表单（字段与上游 signIn 一致）
 *  2. [signInWithCookies]：直接从浏览器复制 ipb_member_id / ipb_pass_hash（+ 可选 igneous）
 *
 * 第二种更可靠：不碰论坛的验证码/二次验证，也不会因为站点改表单而失效。
 */
object EhAuth {

    private const val SIGN_IN_REFERER = "https://forums.e-hentai.org/index.php?act=Login&CODE=00"

    // ------------------------------------------------------------ 账号密码

    fun signIn(username: String, password: String): String {
        if (username.isBlank() || password.isBlank()) throw IOException("用户名或密码为空")

        val form = FormBody.Builder()
            .add("UserName", username)
            .add("PassWord", password)
            .add("submit", "Log me in")
            .add("CookieDate", "1")
            .add("temporary_https", "off")
            .build()

        val request = Request.Builder()
            .url(EhUrl.API_SIGN_IN)
            .header("Referer", SIGN_IN_REFERER)
            .header("Origin", "https://forums.e-hentai.org")
            .header("User-Agent", EhHttp.userAgent)
            .post(form)
            .build()

        val started = System.currentTimeMillis()
        var code = -1
        var err: String? = null
        try {
            EhHttp.client.newCall(request).execute().use { response ->
                code = response.code
                val body = response.body?.string().orEmpty()
                AppLog.i("Auth", "signIn http=$code bodyLen=${body.length}")
                val message = SignInParser.parse(body)
                if (!hasSignedIn()) throw IOException("登录未生效：$message")
                // 登录成功后顺手把 igneous 拿下来（失败不阻塞登录）
                runCatching { prepareExHentai() }
                return message
            }
        } catch (t: Throwable) {
            err = t.message ?: t.javaClass.simpleName
            throw t
        } finally {
            NetLog.log("POST", EhUrl.API_SIGN_IN, code, System.currentTimeMillis() - started, 0, err)
        }
    }

    // ------------------------------------------------------------ Cookie 登录

    /**
     * 用浏览器里复制出来的 cookie 登录（里站推荐）。
     *
     * @param memberId  ipb_member_id
     * @param passHash  ipb_pass_hash
     * @param igneous   可选；不填会自动访问里站尝试获取
     *
     * @return 给用户看的提示文案
     */
    fun signInWithCookies(memberId: String, passHash: String, igneous: String? = null): String {
        val id = memberId.trim()
        val hash = passHash.trim()
        if (id.isBlank() || hash.isBlank()) throw IOException("ipb_member_id 和 ipb_pass_hash 不能为空")

        // 两个站点都要写入：表站用于常规请求，里站用于 exhentai
        listOf(EhUrl.DOMAIN_E, EhUrl.DOMAIN_EX).forEach { domain ->
            EhCookieJar.put(EhCookieJar.KEY_MEMBER_ID, id, domain)
            EhCookieJar.put(EhCookieJar.KEY_PASS_HASH, hash, domain)
        }
        // 表站的内容警告 cookie，避免首屏是警告页
        EhCookieJar.put("nw", "1", EhUrl.DOMAIN_E)

        val ign = igneous?.trim().orEmpty()
        if (ign.isNotEmpty()) {
            EhCookieJar.put(EhCookieJar.KEY_IGNEOUS, ign, EhUrl.DOMAIN_EX)
        } else {
            runCatching { prepareExHentai() }
        }

        // 校验：能进里站才算真的成功（有些账号没有里站权限）
        return when (val status = checkExHentai()) {
            ExStatus.OK -> "Cookie 登录成功，里站可用"
            ExStatus.SAD_PANDA -> throw IOException("Cookie 无效，或该账号没有里站权限（sad panda）")
            ExStatus.NOT_LOGGED_IN -> throw IOException("Cookie 未生效")
            ExStatus.NETWORK_ERROR -> "Cookie 已保存，但当前网络无法验证里站（请检查代理）"
        }
    }

    // ------------------------------------------------------------ 里站

    /** 主动访问里站，让站点下发 igneous。 */
    fun prepareExHentai(): Boolean {
        if (!hasSignedIn()) return false
        if (EhCookieJar.has(EhCookieJar.KEY_IGNEOUS)) return true
        val request = Request.Builder()
            .url(EhUrl.HOST_EX)
            .header("User-Agent", EhHttp.userAgent)
            .build()
        return runCatching {
            EhHttp.client.newCall(request).execute().use { it.code }
            EhCookieJar.has(EhCookieJar.KEY_IGNEOUS)
        }.getOrDefault(false)
    }

    /** 真实请求里站首页，判断到底能不能进。 */
    fun checkExHentai(): ExStatus {
        if (!hasSignedIn()) return ExStatus.NOT_LOGGED_IN

        val request = Request.Builder()
            .url(EhUrl.HOST_EX)
            .header("User-Agent", EhHttp.userAgent)
            .build()

        val started = System.currentTimeMillis()
        var code = -1
        return try {
            EhHttp.client.newCall(request).execute().use { response ->
                code = response.code
                val body = response.body?.string().orEmpty()
                NetLog.log("GET", EhUrl.HOST_EX, code, System.currentTimeMillis() - started, body.length.toLong(), null)
                val status = classifyExResponse(body)
                AppLog.i("Auth", "checkEx http=$code len=${body.length} -> $status")
                status
            }
        } catch (t: Throwable) {
            NetLog.log("GET", EhUrl.HOST_EX, code, System.currentTimeMillis() - started, 0, t.message)
            ExStatus.NETWORK_ERROR
        }
    }

    private fun classifyExResponse(body: String): ExStatus = when {
        body.contains("sad panda", ignoreCase = true) -> ExStatus.SAD_PANDA
        body.length < 1500 -> ExStatus.SAD_PANDA
        body.contains("e-hentai", ignoreCase = true) || body.contains("gallery", ignoreCase = true) -> ExStatus.OK
        else -> ExStatus.SAD_PANDA
    }

    // ------------------------------------------------------------ 状态

    /** 有 member id + pass hash 就算登录（与上游行为一致）。 */
    fun hasSignedIn(): Boolean =
        EhCookieJar.has(EhCookieJar.KEY_MEMBER_ID) && EhCookieJar.has(EhCookieJar.KEY_PASS_HASH)

    fun signOut() {
        EhCookieJar.clear()
        AppLog.i("Auth", "signed out")
    }

    fun setExHentai(ex: Boolean) {
        Settings.isExHentai = ex
        if (ex) prepareExHentai()
    }

    fun isExHentai(): Boolean = Settings.isExHentai

    /** 从论坛页面解析当前登录用户名（失败返回 null）。 */
    fun fetchDisplayName(): String? {
        if (!hasSignedIn()) return null
        val hosts = listOf(
            "https://e-hentai.org/home.php",
            "https://e-hentai.org/",
        )
        val quickClient = EhHttp.client.newBuilder()
            .callTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        for (url in hosts) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", EhHttp.userAgent)
                .build()
            val body = runCatching {
                quickClient.newCall(request).execute().use { it.body?.string().orEmpty() }
            }.getOrNull() ?: continue
            val lower = body.lowercase()
            val idx = lower.indexOf("logged in as")
            AppLog.d("Auth", "name probe $url len=${body.length} hit=$idx")
            if (idx >= 0) {
                val snippet = body.substring(idx, minOf(body.length, idx + 240))
                val m = Regex("logged in as:?\\s*(?:<[^>]+>\\s*)*([^<\\n]{1,40})", RegexOption.IGNORE_CASE)
                    .find(snippet)
                val name = m?.groupValues?.get(1)?.trim()
                if (!name.isNullOrEmpty()) return name
            }
        }
        return null
    }
}
