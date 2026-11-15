/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.ehviewer.client.data.topList;

/**
 * 榜单里的一行。
 *
 * 站点在不同榜单里给的数据形状不一样：画廊榜有 gid/token，标签榜、上传者榜只有
 * 名字与数值，所以这里把所有可能出现的字段都留着，缺省为 null。
 */
public class TopListItem {

    /** 该行指向的页面；上传者榜、标签榜用它做跳转。 */
    public String href;

    /** 画廊 id（画廊榜才有）。 */
    public String gid;

    /** 画廊 token（画廊榜才有）。 */
    public String token;

    /** 标签名或上传者名（标签榜、上传者榜才有）。 */
    public String tag;

    /** 排名依据的数值文本，例如 "1234"。 */
    public String value;

    public TopListItem() {
    }
}
