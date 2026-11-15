// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

import app.eros.shared.settings.AppSettings

/**
 * 标题语言设置（0 英文优先 / 1 日文优先）下的取值规则。
 *
 * 读 [AppSettings.galleryTitleMode] 属于状态读取，Composable 里调用会在设置变更时自动重组。
 */
object GalleryTitles {
    val japaneseFirst: Boolean get() = AppSettings.galleryTitleMode == 1

    /** 当前语言下的标题；两者都为空时返回 null。 */
    fun primaryOrNull(title: String?, titleJpn: String?): String? {
        val en = title?.takeIf { it.isNotBlank() }
        val jp = titleJpn?.takeIf { it.isNotBlank() }
        return if (japaneseFirst) jp ?: en else en ?: jp
    }

    /** 当前语言下的标题；都没有时返回空串（卡片等需要非空文本的地方用）。 */
    fun primary(title: String?, titleJpn: String?): String = primaryOrNull(title, titleJpn) ?: ""

    /** 另一种语言的标题（详情页副标题）；与主标题相同或为空时返回 null。 */
    fun secondary(title: String?, titleJpn: String?): String? {
        val en = title?.takeIf { it.isNotBlank() }
        val jp = titleJpn?.takeIf { it.isNotBlank() }
        val other = if (japaneseFirst) en else jp
        return other?.takeIf { it != primaryOrNull(title, titleJpn) }
    }
}
