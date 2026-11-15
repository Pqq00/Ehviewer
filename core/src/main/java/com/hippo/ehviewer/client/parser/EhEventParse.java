/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.ehviewer.client.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/**
 * 从新闻页里抽出顶部事件横幅。
 *
 * 横幅是 `#eventpane` 的内部 HTML；没有横幅（正常情况）时返回 null。
 */
public final class EhEventParse {

    private static final String EVENT_PANE_ID = "eventpane";

    private EhEventParse() {
    }

    public static String parse(String body) {
        Element pane = Jsoup.parse(body).getElementById(EVENT_PANE_ID);
        return pane == null ? null : pane.html();
    }
}
