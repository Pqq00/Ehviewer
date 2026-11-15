/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.ehviewer.client.data;

/**
 * 更新检查的结果。
 *
 * 站点发布页上的 latest 版本只有两个有用信息：版本号（用来和当前版本比较）
 * 与下载页地址（用来跳转）。其余字段不复用。
 */
public class NewVersion {

    /** 下载页地址。 */
    public String versionUrl;

    /** 版本号文本，例如 "1.9.9"。 */
    public String versionName;

    public NewVersion() {
    }

    /** 从 Parcelable 版本沿袭下来的接口；本项目没有跨进程序列化，恒为 0。 */
    public int describeContents() {
        return 0;
    }
}
