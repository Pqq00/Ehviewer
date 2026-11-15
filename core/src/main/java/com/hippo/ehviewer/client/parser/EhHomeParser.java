/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.ehviewer.client.parser;

import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.client.data.HomeDetail;
import com.hippo.ehviewer.client.exception.EhException;
import com.hippo.ehviewer.core.MiuixLog;
import com.hippo.lib.yorozuya.NumberUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「My Home」账户页解析器。
 *
 * 页面主体是一串 {@code .homebox} 区块，序数固定：
 * 第 1 个是观看额度，第 3 个是 GP 收支明细，第 5 个是管理力。
 * 站点改版只会改区块内部的文案，所以所有取数都带兜底值（-1），
 * 任何一处结构对不上都不影响其它区块。
 */
public final class EhHomeParser {

    private static final String TAG = "EhHomeParser";
    private static final String HOME_BOX_CLASS = "homebox";

    /** 区块在页面里的固定位置（0-based）。 */
    private static final int BOX_QUOTA = 0;
    private static final int BOX_GP = 2;
    private static final int BOX_MODERATION = 4;

    /** 额度文案，新版多一句「你可以花 X GP 重置」。 */
    private static final Pattern QUOTA_TEXT = Pattern.compile(
            "<p>You are currently at <strong>(.+?)</strong> towards your account limit of <strong>(.+?)</strong>.</p>\n"
                    + "<p>You can reset your image quota by spending <strong>(.+?)</strong> GP.</p>",
            Pattern.DOTALL);

    /** 额度文案旧版。 */
    private static final Pattern QUOTA_TEXT_LEGACY = Pattern.compile(
            "<p>You are currently at <strong>(\\d+)</strong> towards a limit of <strong>(\\d+)</strong>.</p>"
                    + ".+?<p>Reset Cost: <strong>(\\d+)</strong> GP</p>",
            Pattern.DOTALL);

    private EhHomeParser() {
    }

    /**
     * @param body home.php 的原始 HTML
     * @return 能取到的字段都填好，取不到的保持默认值
     */
    public static HomeDetail parse(String body) throws EhException {
        HomeDetail detail = new HomeDetail();
        try {
            Elements boxes = Jsoup.parse(body).getElementsByClass(HOME_BOX_CLASS);
            if (boxes.isEmpty()) {
                return detail;
            }
            readQuota(boxes.get(BOX_QUOTA), detail);
            readGpBalance(boxes.get(BOX_GP), detail);
            readModerationPower(boxes.get(BOX_MODERATION), detail);
        } catch (Exception e) {
            MiuixLog.e(TAG, "数据结构错误");
            Analytics.recordException(e);
        }
        return detail;
    }

    private static void readQuota(Element box, HomeDetail detail) {
        String html = box.html();
        Matcher matcher = QUOTA_TEXT.matcher(html);
        if (!matcher.find()) {
            matcher = QUOTA_TEXT_LEGACY.matcher(html);
            if (!matcher.find()) {
                return;
            }
        }
        detail.setUsed(group(matcher, 1));
        detail.setTotal(group(matcher, 2));
        detail.setResetCost(group(matcher, 3));
    }

    private static void readGpBalance(Element box, HomeDetail detail) {
        Element table = box.getAllElements().get(0);
        Elements rows = table.child(0).child(0).children();
        detail.setFromGalleryVisits(cellCount(rows, 0));
        detail.setFromTorrentCompletions(cellCount(rows, 1));
        detail.setFromArchiveDownloads(cellCount(rows, 2));
        detail.setFromHentaiAtHome(cellCount(rows, 3));
    }

    private static void readModerationPower(Element box, HomeDetail detail) {
        Element row = box.getAllElements().get(0).child(0).children().get(0);
        long power = toLong(row.child(0).child(0).child(1).text());
        detail.setCurrentModerationPower(power);
    }

    /** 第 index 行第一格里的数字。 */
    private static long cellCount(Elements rows, int index) {
        return toLong(rows.get(index).child(0).text());
    }

    private static long group(Matcher matcher, int index) {
        String value = matcher.group(index);
        return value == null ? 0L : toLong(value);
    }

    private static long toLong(String text) {
        return NumberUtils.parseLongSafely(text.replace(",", ""), -1L);
    }
}
