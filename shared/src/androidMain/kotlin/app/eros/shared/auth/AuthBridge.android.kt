// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.auth

import android.content.Context
import app.eros.shared.settings.AndroidPlatform
import com.hippo.ehviewer.net.EhAuth
import com.hippo.ehviewer.net.EhCookieJar
import com.hippo.ehviewer.net.HttpCacheBridgeHolder

private const val PREFS = "eros_auth"
private const val KEY_COOKIES = "cookies"

private fun prefs() = AndroidPlatform.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private var wired = false

private fun ensureWired() {
    if (wired) return
    wired = true
    // cookie 变化即落盘（Keystore 加密），下次启动还能保持登录
    EhCookieJar.onChange = { data ->
        val encrypted = AndroidCookieCipher.encrypt(data)
        if (encrypted != null) {
            runCatching { prefs().edit().putString(KEY_COOKIES, encrypted).apply() }
        }
    }
}

actual fun signIn(username: String, password: String): AuthResult {
    ensureWired()
    return try {
        val message = EhAuth.signIn(username.trim(), password)
        HttpCacheBridgeHolder.clear() // 登录态变化后旧页面（含未登录版）不再可用
        AuthResult(true, message)
    } catch (t: Throwable) {
        AuthResult(false, t.message ?: t.javaClass.simpleName)
    }
}

actual fun signOut() {
    ensureWired()
    EhAuth.signOut()
    HttpCacheBridgeHolder.clear()
    runCatching { prefs().edit().remove(KEY_COOKIES).apply() }
}

actual fun isSignedIn(): Boolean {
    ensureWired()
    return EhAuth.hasSignedIn()
}

actual fun applyExHentai(enabled: Boolean) {
    ensureWired()
    EhAuth.setExHentai(enabled)
    HttpCacheBridgeHolder.clear() // e / ex 域名不同，但清掉可避免切换后的旧页面串用
}

actual fun restoreAuth(): Boolean {
    ensureWired()
    val saved = runCatching { prefs().getString(KEY_COOKIES, null) }.getOrNull()
    if (!saved.isNullOrBlank()) {
        val plain = if (CookieCipherText.isEncrypted(saved)) {
            AndroidCookieCipher.decrypt(saved)
        } else {
            // 老版本明文 → 读取后立刻按加密格式重写
            saved.also { legacy ->
                AndroidCookieCipher.encrypt(legacy)?.let { encrypted ->
                    runCatching { prefs().edit().putString(KEY_COOKIES, encrypted).apply() }
                }
            }
        }
        if (plain.isNullOrBlank()) {
            // 密钥丢失 / 密文损坏：清掉失效数据，按未登录处理
            runCatching { prefs().edit().remove(KEY_COOKIES).apply() }
            return false
        }
        EhCookieJar.import(plain)
    }
    return EhAuth.hasSignedIn()
}

actual fun signInWithCookies(memberId: String, passHash: String, igneous: String?): AuthResult {
    ensureWired()
    return try {
        HttpCacheBridgeHolder.clear()
        AuthResult(true, EhAuth.signInWithCookies(memberId, passHash, igneous))
    } catch (t: Throwable) {
        AuthResult(false, t.message ?: t.javaClass.simpleName)
    }
}

actual fun checkExHentai(): String {
    ensureWired()
    return EhAuth.checkExHentai().name
}
actual fun signedInAccount(): String? {
    ensureWired()
    return com.hippo.ehviewer.net.EhCookieJar.get("ipb_member_id")
}
actual fun cookieValue(name: String): String? {
    ensureWired()
    return com.hippo.ehviewer.net.EhCookieJar.get(name)
}

actual fun signedInDisplayName(): String? {
    ensureWired()
    return com.hippo.ehviewer.net.EhAuth.fetchDisplayName()
}