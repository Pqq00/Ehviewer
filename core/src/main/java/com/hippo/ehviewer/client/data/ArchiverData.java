/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.ehviewer.client.data;

/**
 * 「下载原档」面板的一组报价，来自 archiver.php。
 *
 * 站点对同一画廊给出两档：原始档与重采样档。两档各自带价格、体积、下载地址，
 * 余额不够时价格照旧返回，能不能点是界面自己判断的事。
 */
public class ArchiverData {

    /** 账户余额，形如 "123456 Gp"。 */
    public String funds;

    /** 原始档价格，形如 "1234 Gp"。 */
    public String originalCost;

    /** 原始档体积，形如 "1.2 GB"。 */
    public String originalSize;

    /** 原始档下载地址。 */
    public String originalUrl;

    /** 重采样档价格。 */
    public String resampleCost;

    /** 重采样档体积。 */
    public String resampleSize;

    /** 重采样档下载地址。 */
    public String resampleUrl;

    public ArchiverData() {
    }
}
