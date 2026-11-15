/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.util;

/**
 * 上游借助 Android 的 Parcel 做深拷贝。本实现不依赖 Android API，
 * 暂时退化为"原样返回"。
 *
 * 注意：调用方（GalleryDetail.copy）原本是为了拿一份可编辑的副本；在当前实现里
 * 编辑会直接作用在原对象上。等详情页支持编辑/评分时再补真正的深拷贝。
 */
public final class DataUtils {

    private DataUtils() {
    }

    public static <T> T copy(T input) {
        return input;
    }
}
