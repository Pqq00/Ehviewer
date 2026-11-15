// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.auth

data class AuthResult(val ok: Boolean, val message: String)

/** 登录（走 E-Hentai 论坛登录表单）。阻塞调用，UI 层自己切线程。 */
expect fun signIn(username: String, password: String): AuthResult

/** 用浏览器复制的 cookie 登录（里站推荐）。 */
expect fun signInWithCookies(memberId: String, passHash: String, igneous: String?): AuthResult

/** 检测里站可用性，返回 OK / SAD_PANDA / NOT_LOGGED_IN / NETWORK_ERROR。 */
expect fun checkExHentai(): String

expect fun signOut()

expect fun isSignedIn(): Boolean

/** 表站 / 里站切换；切到里站会尝试获取 igneous cookie。 */
expect fun applyExHentai(enabled: Boolean)

/**
 * 启动时调用：从平台存储恢复 cookie。
 * 返回恢复后是否处于已登录状态。
 */
expect fun restoreAuth(): Boolean
/** 当前登录账号的 ipb_member_id（未登录返回 null）。 */
expect fun signedInAccount(): String?
/** 读取登录 cookie 的值（用于展示/复制）。 */
expect fun cookieValue(name: String): String?

/** 从论坛页面读取当前登录用户的显示名（失败返回 null）。 */
expect fun signedInDisplayName(): String?