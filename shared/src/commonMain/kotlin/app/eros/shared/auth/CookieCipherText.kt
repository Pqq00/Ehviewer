// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.auth

import kotlin.io.encoding.Base64

/**
 * 登录 cookie 的落盘格式。
 *
 * 平台层用 Keystore 加密后按 `v1:<base64(iv + ciphertext)>` 存储；
 * 老版本直接存的是明文 `name\tvalue...`，读取时按前缀区分并做一次性迁移。
 */
object CookieCipherText {

    private const val PREFIX = "v1:"

    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    fun encode(iv: ByteArray, payload: ByteArray): String =
        PREFIX + Base64.encode(iv + payload)

    /** 解出 `iv + ciphertext`；不是加密格式或 base64 损坏时返回 null。 */
    fun decode(stored: String): ByteArray? {
        if (!isEncrypted(stored)) return null
        return runCatching { Base64.decode(stored.removePrefix(PREFIX)) }.getOrNull()
    }
}
