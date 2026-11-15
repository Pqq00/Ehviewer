// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.reader

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 阅读模式。
 *
 * 序号会写进持久化，**不要重排已有项**；[AUTO] 追加在末尾（旧数据里不会有它）。
 */
enum class ReaderMode(val label: String) {
    LTR("左→右"),
    RTL("右→左"),
    DUAL("双页"),
    VERTICAL("连续"),
    AUTO("自动");

    /**
     * 底栏「翻页模式」按钮的下一档：**跳过 [AUTO]**。
     *
     * 自动档按屏幕方向抉择（竖屏=单页、横屏=双页），按按钮切到它容易让人以为没生效，
     * 所以只在阅读设置的「翻页模式」里可选，快速切换按钮不再经过它。
     */
    fun next(): ReaderMode {
        val selectable = entries.filter { it != AUTO }
        val index = selectable.indexOf(this)
        return if (index < 0) selectable.first() else selectable[(index + 1) % selectable.size]
    }

    /** 横向翻页（含自动识别后的横向）。 */
    val horizontal: Boolean get() = this != VERTICAL

    companion object {
        fun fromOrdinal(value: Int): ReaderMode = entries.getOrElse(value) { AUTO }
    }
}

/** 单页图片的缩放方式。 */
enum class ReaderFit(val label: String) {
    WIDTH("适应宽度"),
    HEIGHT("适应高度"),
    ORIGINAL("原始尺寸");

    companion object {
        fun fromOrdinal(value: Int): ReaderFit = entries.getOrElse(value) { WIDTH }
    }
}

/** 单页图片的加载状态（带下载进度）。 */
sealed interface PageImage {
    data object Pending : PageImage

    /** 正在获取该页的 /s/ 地址（还没开始下图片）。 */
    data object ResolvingAddress : PageImage

    data class Loading(val progress: Float) : PageImage

    data class Ready(val bitmap: ImageBitmap) : PageImage

    data class Failed(val message: String) : PageImage
}

/** 阅读器里的底部弹层。 */
enum class ReaderSheet { None, Jump, Settings }
