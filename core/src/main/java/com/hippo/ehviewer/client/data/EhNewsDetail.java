/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.ehviewer.client.data;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 首页新闻页的惰性视图：原始 HTML 进来，只在需要时切出两段。
 *
 * - 顶部事件横幅（`#eventpane`）：站点约定只有恰好三行内容时才展示
 * - 正文：整页（用于拼进 WebView）
 */
public class EhNewsDetail {

    /** 新闻页原始 HTML。 */
    public String webData;

    private String eventPane;

    public EhNewsDetail() {
    }

    public EhNewsDetail(String webData) {
        this.webData = webData;
    }

    /** 顶部事件横幅的内部 HTML；不满足三行条件时为 null。 */
    public String getEventPane() {
        if (eventPane != null) {
            return eventPane;
        }
        Element pane = parse().getElementById("eventpane");
        if (pane != null && pane.childrenSize() == 3) {
            eventPane = pane.html();
        }
        return eventPane;
    }

    /** 整页 HTML：取解析后文档的第一个子节点，丢掉 jsoup 补的外壳。 */
    public String getHtmlData() {
        return parse().child(0).outerHtml();
    }

    private Document parse() {
        return Jsoup.parse(webData == null ? "" : webData);
    }
}
