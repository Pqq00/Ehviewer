// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.reader

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 阅读模式。
 *
 * 序号会写进持久化，**不要重排已有项**。
 *
 * 横屏不再自动切双页：只有显式选 [DUAL] 才并排两页，其余模式横竖屏都按单页 / 连续走。
 */
enum class ReaderMode(val label: String) {
    LTR("左→右"),
    RTL("右→左"),
    DUAL("双页"),
    VERTICAL("连续");

    /** 底栏「翻页模式」按钮的下一档。 */
    fun next(): ReaderMode = entries[(ordinal + 1) % entries.size]

    /** 横向翻页（双页也算横向）。 */
    val horizontal: Boolean get() = this != VERTICAL

    companion object {
        fun fromOrdinal(value: Int): ReaderMode = entries.getOrElse(value) { LTR }
    }
}

/**
 * 单页图片的缩放方式（只影响双页模式）。
 *
 * 单页模式始终整页可见：横屏按宽度铺满会把上下裁掉，而单页没有可拖动的地方。
 */
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
