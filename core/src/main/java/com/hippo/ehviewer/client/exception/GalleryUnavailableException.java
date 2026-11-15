/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.ehviewer.client.exception;

/**
 * 画廊页能打开，但站点把它标成不可用（下架、受限或无权限）。
 *
 * 与「gdata 确认已删除」区分开：那种情况由解析器判成删除，不走这个异常。
 */
public class GalleryUnavailableException extends EhException {

    private static final String MESSAGE = "此画廊已被下架\nThis gallery is unavailable";

    public GalleryUnavailableException() {
        super(MESSAGE);
    }
}
