/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.ehviewer.client.parser;

import com.hippo.ehviewer.client.data.EhTopListDetail;
import com.hippo.ehviewer.client.data.topList.TopListInfo;
import com.hippo.ehviewer.client.data.topList.TopListItem;
import com.hippo.ehviewer.client.data.topList.TopListItemArray;
import com.hippo.ehviewer.client.exception.EhException;
import com.hippo.ehviewer.client.exception.OffensiveException;
import com.hippo.ehviewer.client.exception.PiningException;
import com.hippo.util.JsoupUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 排行榜总览页（toplist.php）解析器。
 *
 * 页面结构：`.ido` 的第一个子元素是标题，之后每两个子元素是一块——
 * 奇数位是榜单容器，偶数位是分隔。榜单容器内部再分四个时间窗：
 * 子元素 1..4 依次是「全部 / 今年 / 本月 / 昨日」，每个时间窗里
 * `.tun` 条目就是榜单行。
 */
public final class TopListParser {

    private static final String CONTAINER_CLASS = "ido";
    private static final String ENTRY_CLASS = "tun";

    /** 一个时间窗最多展示多少条（站点固定 10 条）。 */
    private static final int SLOTS = 10;

    /** 标题之后的榜单块起点与步长。 */
    private static final int FIRST_BLOCK = 1;
    private static final int BLOCK_STRIDE = 2;

    /** 与 {@link #parseBlock} 的调用顺序一一对应。 */
    private static final EhTopListDetail.ListType[] BLOCK_TYPES = {
            EhTopListDetail.ListType.GALLERY,
            EhTopListDetail.ListType.UPLOADER,
            EhTopListDetail.ListType.TAGGING,
            EhTopListDetail.ListType.HENTAI_HOME,
            EhTopListDetail.ListType.EH_TRACKER,
            EhTopListDetail.ListType.CLEANUP,
            EhTopListDetail.ListType.RATING_AND_REVIEWING,
    };

    private static final String OFFENSIVE_MARK = "<p>(And if you choose to ignore this warning, "
            + "you lose all rights to complain about it in the future.)</p>";
    private static final String PINING_MARK = "<p>This gallery is pining for the fjords.</p>";
    private static final Pattern ERROR_BLOCK = Pattern.compile("<div class=\"d\">\n<p>([^<]+)</p>");

    private TopListParser() {
    }

    public static EhTopListDetail parse(String body) throws EhException {
        rejectUnavailable(body);

        Elements blocks = Jsoup.parse(body).getElementsByClass(CONTAINER_CLASS).get(0).children();

        EhTopListDetail detail = new EhTopListDetail();
        detail.title = blocks.get(0).text();
        for (int i = 0; i < BLOCK_TYPES.length; i++) {
            TopListInfo info = parseBlock(blocks.get(FIRST_BLOCK + i * BLOCK_STRIDE), BLOCK_TYPES[i]);
            put(detail, info);
        }
        return detail;
    }

    /** 站点用固定文案表示「内容不可见」，优先级高于错误框。 */
    private static void rejectUnavailable(String body) throws EhException {
        if (body.contains(OFFENSIVE_MARK)) {
            throw new OffensiveException();
        }
        if (body.contains(PINING_MARK)) {
            throw new PiningException();
        }
        Matcher error = ERROR_BLOCK.matcher(body);
        if (error.find()) {
            throw new EhException(error.group(1));
        }
    }

    private static TopListInfo parseBlock(Element block, EhTopListDetail.ListType type) {
        TopListInfo info = new TopListInfo();
        info.type = type;
        Elements windows = block.children();
        info.allTimeTopList = parseWindow(windows.get(1));
        info.pastYearTopList = parseWindow(windows.get(2));
        info.pastMonthTopList = parseWindow(windows.get(3));
        info.yesterdayTopList = parseWindow(windows.get(4));
        return info;
    }

    private static TopListItemArray parseWindow(Element window) {
        Elements entries = JsoupUtils.getElementsByClass(window.child(1).child(0), ENTRY_CLASS);
        TopListItem[] items = new TopListItem[SLOTS];
        int limit = Math.min(entries.size(), SLOTS);
        for (int i = 0; i < limit; i++) {
            Element anchor = entries.get(i).child(0);
            TopListItem item = new TopListItem();
            item.value = anchor.text();
            item.href = anchor.attr("href");
            items[i] = item;
        }
        TopListItemArray array = new TopListItemArray();
        array.itemArray = items;
        return array;
    }

    private static void put(EhTopListDetail detail, TopListInfo info) {
        switch (info.type) {
            case GALLERY:
                detail.galleryTopListInfo = info;
                break;
            case UPLOADER:
                detail.uploaderTopListInfo = info;
                break;
            case TAGGING:
                detail.taggingTopListInfo = info;
                break;
            case HENTAI_HOME:
                detail.hentaiHomeTopListInfo = info;
                break;
            case EH_TRACKER:
                detail.ehTrackerTopListInfo = info;
                break;
            case CLEANUP:
                detail.cleanUpTopListInfo = info;
                break;
            case RATING_AND_REVIEWING:
                detail.ratingAndReviewingTopListInfo = info;
                break;
            default:
                break;
        }
    }
}
