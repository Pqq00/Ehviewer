// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.auth

import com.hippo.ehviewer.net.EhAuth
import com.hippo.ehviewer.net.EhCookieJar
import java.util.prefs.Preferences

private val node: Preferences by lazy { Preferences.userRoot().node("app/eros/auth") }
private var wired = false

private fun ensureWired() {
    if (wired) return
    wired = true
    EhCookieJar.onChange = { data -> runCatching { node.put("cookies", data) } }
}

actual fun signIn(username: String, password: String): AuthResult {
    ensureWired()
    return try {
        AuthResult(true, EhAuth.signIn(username.trim(), password))
    } catch (t: Throwable) {
        AuthResult(false, t.message ?: t.javaClass.simpleName)
    }
}

actual fun signOut() {
    ensureWired()
    EhAuth.signOut()
    runCatching { node.remove("cookies") }
}

actual fun isSignedIn(): Boolean {
    ensureWired()
    return EhAuth.hasSignedIn()
}

actual fun applyExHentai(enabled: Boolean) {
    ensureWired()
    EhAuth.setExHentai(enabled)
}

actual fun restoreAuth(): Boolean {
    ensureWired()
    val saved = runCatching { node.get("cookies", "") }.getOrNull()
    if (!saved.isNullOrBlank()) EhCookieJar.import(saved)
    return EhAuth.hasSignedIn()
}

actual fun signInWithCookies(memberId: String, passHash: String, igneous: String?): AuthResult {
    ensureWired()
    return try {
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