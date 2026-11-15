/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.ehviewer.client.data.topList;

/**
 * 一个时间窗内的榜单条目（解析器一次性填满 {@link #itemArray}）。
 */
public class TopListItemArray {

    public TopListItem[] itemArray;

    public TopListItemArray() {
    }

    /** 条目数；解析失败时会退化成 0 而不是抛空指针。 */
    public int length() {
        return itemArray == null ? 0 : itemArray.length;
    }

    public TopListItem get(int index) {
        return itemArray == null ? null : itemArray[index];
    }
}
