// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.auth

import androidx.compose.runtime.Immutable

/**
 * 从剪贴板文本里解析出来的 E-Hentai 登录 cookie。
 *
 * 三个字段都可能为 null（没识别到）。
 */
@Immutable
data class EhCookies(
    val memberId: String? = null,
    val passHash: String? = null,
    val igneous: String? = null,
) {
    /** 是否至少识别到一项。 */
    val any: Boolean
        get() = !memberId.isNullOrBlank() || !passHash.isNullOrBlank() || !igneous.isNullOrBlank()

    /** 登录必需但没识别到的字段名。 */
    val missing: List<String>
        get() = buildList {
            if (memberId.isNullOrBlank()) add(NAME_MEMBER_ID)
            if (passHash.isNullOrBlank()) add(NAME_PASS_HASH)
        }
}

private const val NAME_MEMBER_ID = "ipb_member_id"
private const val NAME_PASS_HASH = "ipb_pass_hash"
private const val NAME_IGNEOUS = "igneous"

/**
 * 解析用户从浏览器 / 其他客户端复制来的登录 cookie。
 *
 * 支持的写法：
 * - `ipb_member_id=123; ipb_pass_hash=abc; igneous=xyz`（最常见，分号分隔，允许夹带别的 cookie）
 * - `Cookie: ipb_member_id=123; ...`（带 `Cookie:` 前缀）
 * - 用换行分隔的同样格式
 * - 只有值、没有名字：按顺序/形态猜测（纯数字 → member_id，10 位十六进制 → pass_hash，更长的十六进制 → igneous）
 */
fun parseEhCookies(text: String): EhCookies {
    val raw = text.trim()
    if (raw.isEmpty()) return EhCookies()

    // 去掉 "Cookie:" 之类前缀
    val body = raw.substringAfter(':', raw).ifBlank { raw }
    // 统一分隔符：分号、换行、制表符都当作分隔
    val segments = body
        .replace('\r', ';')
        .replace('\n', ';')
        .replace('\t', ';')
        .split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    var memberId: String? = null
    var passHash: String? = null
    var igneous: String? = null
    val bare = mutableListOf<String>()

    for (segment in segments) {
        val eq = segment.indexOf('=')
        if (eq <= 0) {
            bare += segment
            continue
        }
        val key = segment.substring(0, eq).trim().lowercase()
        val value = segment.substring(eq + 1).trim().trim('"', '\'')
        if (value.isEmpty()) continue
        when (key) {
            NAME_MEMBER_ID, "member_id", "ipb_memberid", "memberid" -> memberId = value
            NAME_PASS_HASH, "pass_hash", "ipb_passhash", "passhash" -> passHash = value
            NAME_IGNEOUS, "ign" -> igneous = value
            else -> Unit // sk / hpHm 之类的无关 cookie
        }
    }

    if (memberId == null && passHash == null && igneous == null) {
        // 没有名字，按形态猜
        for (value in bare.flatMap { it.split(' ', ',', '|') }) {
            val v = value.trim().trim('"', '\'')
            if (v.isEmpty()) continue
            when {
                v.all { it.isDigit() } && memberId == null -> memberId = v
                isHex(v) && v.length == 10 && passHash == null -> passHash = v
                isHex(v) && v.length > 10 && igneous == null -> igneous = v
            }
        }
    }

    return EhCookies(
        memberId = memberId?.takeIf { it.isNotBlank() },
        passHash = passHash?.takeIf { it.isNotBlank() },
        igneous = igneous?.takeIf { it.isNotBlank() },
    )
}

private fun isHex(value: String): Boolean =
    value.isNotEmpty() && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
