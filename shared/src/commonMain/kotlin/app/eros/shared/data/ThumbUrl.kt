// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

import java.util.concurrent.atomic.AtomicInteger

/**
 * 列表封面 URL 归一化。
 *
 * E-Hentai 的缩略图地址常带尺寸后缀（如 `xxx-1000.jpg`），而信息流卡片只显示约 190dp 宽，
 * 直接下 1000px 的图纯属浪费。这里把明显偏大的尺寸改写成 250px；
 * 改写失败（如该尺寸不存在）由调用方回退原始 URL。
 */
object ThumbUrl {

    private val SIZE_RE = Regex("""-(\d{3,4})\.(jpg|jpeg|png|webp|gif)$""", RegexOption.IGNORE_CASE)
    private val logged = AtomicInteger(0)

    /** 卡片用的封面地址（必要时降尺寸）。 */
    fun forCard(url: String): String {
        val match = SIZE_RE.find(url) ?: return url
        val size = match.groupValues[1].toIntOrNull() ?: return url
        if (size <= 250) return url
        val ext = match.groupValues[2]
        val normalized = url.substring(0, match.range.first) + "-250." + ext
        if (logged.getAndIncrement() < 8) println("[Thumb] $url -> $normalized")
        return normalized
    }
}
