// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.data

/**
 * 用户标签：已关注（watched）或已隐藏（hidden）。
 *
 * 站点用 `usertag_<id>` 这种字符串做标识，展示时用 [tagName]，
 * 隐藏标签还带颜色与权重。
 */
class UserTag {

    private companion object {
        const val ID_PREFIX = "usertag_"
    }

    /** 形如 `usertag_123`。 */
    var userTagId: String? = null

    var tagName: String? = null

    var watched: Boolean = false

    var hidden: Boolean = false

    /** 颜色，形如 `#ff0000`；只有隐藏标签有。 */
    var color: String? = null

    /** 权重；只有隐藏标签有。 */
    var tagWeight: Int = 0

    /** 从 [userTagId] 取出数字 id；格式不符时返回 -1。 */
    fun id(): Long {
        val raw = userTagId ?: return -1L
        if (!raw.startsWith(ID_PREFIX)) {
            return -1L
        }
        return raw.substring(ID_PREFIX.length).toLongOrNull() ?: -1L
    }
}
