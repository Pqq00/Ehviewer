/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.ehviewer.client.data;

import com.hippo.ehviewer.client.data.topList.TopListInfo;

/**
 * toplist.php 一页上的全部榜单。
 *
 * 站点按分类给七个榜，顺序固定；{@link #get(int)} 用的是这个顺序。
 */
public class EhTopListDetail {

    /** 七个榜，顺序即界面顺序。 */
    public enum ListType {
        GALLERY,
        UPLOADER,
        TAGGING,
        HENTAI_HOME,
        EH_TRACKER,
        CLEANUP,
        RATING_AND_REVIEWING,
    }

    public String title;

    public TopListInfo galleryTopListInfo;
    public TopListInfo uploaderTopListInfo;
    public TopListInfo taggingTopListInfo;
    public TopListInfo hentaiHomeTopListInfo;
    public TopListInfo ehTrackerTopListInfo;
    public TopListInfo cleanUpTopListInfo;
    public TopListInfo ratingAndReviewingTopListInfo;

    public EhTopListDetail() {
    }

    /** 按 {@link ListType} 的声明顺序取榜；越界返回 null。 */
    public TopListInfo get(int index) {
        switch (index) {
            case 0:
                return galleryTopListInfo;
            case 1:
                return uploaderTopListInfo;
            case 2:
                return taggingTopListInfo;
            case 3:
                return hentaiHomeTopListInfo;
            case 4:
                return ehTrackerTopListInfo;
            case 5:
                return cleanUpTopListInfo;
            case 6:
                return ratingAndReviewingTopListInfo;
            default:
                return null;
        }
    }
}
