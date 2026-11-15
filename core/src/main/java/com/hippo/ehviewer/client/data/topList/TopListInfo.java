/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.ehviewer.client.data.topList;

import com.hippo.ehviewer.client.data.EhTopListDetail;

/**
 * 一个榜单的四种时间窗（昨日 / 本月 / 今年 / 全部）。
 */
public class TopListInfo {

    private static final int TIME_WINDOWS = 4;

    public TopListItemArray allTimeTopList;
    public TopListItemArray pastYearTopList;
    public TopListItemArray pastMonthTopList;
    public TopListItemArray yesterdayTopList;

    public String title;
    public EhTopListDetail.ListType type;

    public TopListInfo() {
    }

    /**
     * 按界面里的标签顺序取：0 昨日、1 本月、2 今年、3 全部。
     */
    public TopListItemArray get(int index) {
        switch (index) {
            case 0:
                return yesterdayTopList;
            case 1:
                return pastMonthTopList;
            case 2:
                return pastYearTopList;
            case 3:
                return allTimeTopList;
            default:
                return null;
        }
    }

    public int size() {
        return TIME_WINDOWS;
    }
}
