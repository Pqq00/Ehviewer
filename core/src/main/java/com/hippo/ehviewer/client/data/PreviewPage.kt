// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.data

/**
 * 预览缩略图的一页。
 *
 * 画廊的预览图按页返回，一页固定 20 张；除缩略图外还带上「总共有多少页预览」，
 * 用来判断还能不能继续往下取。
 */
class PreviewPage(
    /** 本页的缩略图，通常 20 个。 */
    val previews: List<GalleryPreview>,
    /** 预览总页数（不是本页张数）。 */
    val totalPreviewPages: Int,
)
