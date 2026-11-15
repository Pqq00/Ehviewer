/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.ehviewer.sync;

import com.hippo.ehviewer.client.data.GalleryInfo;

import java.util.List;

/**
 * 标签补全的占位实现。
 *
 * 列表解析完成后会构造它并调用 {@link #execute()}，但 core 层不做标签补全——
 * 真正的补全在 shared 层用 api.php 批量完成（gdata + 标签库翻译）。
 * 保留空实现是为了不改动解析器的调用点。
 */
public class GalleryListTagsSyncTask {

    public GalleryListTagsSyncTask(List<GalleryInfo> infos) {
    }

    public void execute() {
    }
}
