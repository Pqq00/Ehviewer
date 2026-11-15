/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.ehviewer.client.data;

/**
 * 首页账户面板（GP 收支）的数据。
 *
 * 每个副本额度都以「数值 + Gp」的形式展示，但额度本身要参与计算，
 * 所以原始值用 long 存，格式化只发生在 getter 上。
 */
public class HomeDetail {

    private static final String UNIT = " Gp";

    private long used;
    private long total;
    private long resetCost;
    private long fromGalleryVisits;
    private long fromTorrentCompletions;
    private long fromArchiveDownloads;
    private long fromHentaiAtHome;
    private long currentModerationPower;

    public HomeDetail() {
    }

    public long getUsed() {
        return used;
    }

    public void setUsed(long used) {
        this.used = used;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    /** 重置观看额度所需花费，带单位。 */
    public String getResetCost() {
        return formatted(resetCost);
    }

    /** 同 {@link #getResetCost()} 的裸数值，供计算使用。 */
    public long resetCost() {
        return resetCost;
    }

    public void setResetCost(long resetCost) {
        this.resetCost = resetCost;
    }

    public String getFromGalleryVisits() {
        return formatted(fromGalleryVisits);
    }

    public void setFromGalleryVisits(long fromGalleryVisits) {
        this.fromGalleryVisits = fromGalleryVisits;
    }

    public String getFromTorrentCompletions() {
        return formatted(fromTorrentCompletions);
    }

    public void setFromTorrentCompletions(long fromTorrentCompletions) {
        this.fromTorrentCompletions = fromTorrentCompletions;
    }

    public String getFromArchiveDownloads() {
        return formatted(fromArchiveDownloads);
    }

    public void setFromArchiveDownloads(long fromArchiveDownloads) {
        this.fromArchiveDownloads = fromArchiveDownloads;
    }

    public String getFromHentaiAtHome() {
        return formatted(fromHentaiAtHome);
    }

    public void setFromHentaiAtHome(long fromHentaiAtHome) {
        this.fromHentaiAtHome = fromHentaiAtHome;
    }

    /** 管理力，纯数字展示，不带单位。 */
    public String getCurrentModerationPower() {
        return Long.toString(currentModerationPower);
    }

    public void setCurrentModerationPower(long currentModerationPower) {
        this.currentModerationPower = currentModerationPower;
    }

    private static String formatted(long value) {
        return value + UNIT;
    }
}
