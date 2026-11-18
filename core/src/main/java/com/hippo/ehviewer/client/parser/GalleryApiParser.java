/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.client.parser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.client.data.GalleryInfo;

import com.hippo.lib.yorozuya.NumberUtils;

import java.util.List;

public class GalleryApiParser {

    /**
     * 一次 gdata 补全的账目。
     *
     * `api.php` 对已删除/受限的画廊不报错，只是少返回几条或返回带 `error` 的条目；
     * 补全失败以前完全静默，界面上的列表就一直是「标签空白」。有了这些数字，
     * 至少能在 netlog 里看出是哪一步丢的。
     */
    public static class Stats {
        /** 本次请求的画廊数（= 传入列表大小）。 */
        public final int requested;
        /** `gmetadata` 里的条目数。 */
        public int returned;
        /** 返回条目里能在列表中按 gid 找到的。 */
        public int matched;
        /** 真正补全成功的。 */
        public int updated;
        /** 返回了但带 error / 没有 title（已删除、被拒）。 */
        public int skipped;
        /** 请求了却没拿到可用条目（被隐藏或整批没返回）。 */
        public int missing;

        Stats(int requested) {
            this.requested = requested;
        }

        /** 有没有对不上账的条目：有就值得记一条日志。 */
        public boolean hasGaps() {
            return skipped > 0 || missing > 0;
        }

        public String summary() {
            return "请求 " + requested + "，返回 " + returned + "，更新 " + updated
                    + "，跳过 " + skipped + "，缺失 " + missing;
        }
    }

    public static Stats parse(String body, List<GalleryInfo> galleryInfoList) {
        Stats stats = new Stats(galleryInfoList.size());
        JSONObject jo = JSON.parseObject(body);
        JSONArray ja = jo == null ? null : jo.getJSONArray("gmetadata");
        if (ja == null) {
            stats.missing = stats.requested;
            return stats;
        }
        stats.returned = ja.size();

        for (int i = 0, length = ja.size(); i < length; i++) {
            JSONObject g = ja.getJSONObject(i);
            if (g == null) {
                stats.skipped++;
                continue;
            }
            long gid = g.getLongValue("gid");
            GalleryInfo gi = getGalleryInfoByGid(galleryInfoList, gid);
            if (gi == null) {
                stats.missing++;
                continue;
            }
            stats.matched++;
            // A removed or rejected gallery has "error" and no title. Skip it so one bad
            // item does not abort the rest of this batch.
            if (g.containsKey("error") || g.get("title") == null) {
                stats.skipped++;
                continue;
            }
            gi.title = ParserUtils.trim(g.getString("title"));
            gi.titleJpn = ParserUtils.trim(g.getString("title_jpn"));
            gi.category = EhUtils.getCategory(g.getString("category"));
            gi.thumb = EhUtils.handleThumbUrlResolution(g.getString("thumb"));
            gi.uploader = g.getString("uploader");
            gi.posted = ParserUtils.formatDate(ParserUtils.parseLong(g.getString("posted"), 0) * 1000);
            gi.rating = NumberUtils.parseFloatSafely(ParserUtils.trim(g.getString("rating")), 0.0f);
            // tags
            JSONArray tagJa = g.getJSONArray("tags");
            int tagLength = tagJa == null ? 0 : tagJa.size();
            String[] tags = new String[tagLength];
            for (int j = 0; j < tagLength; j++) {
                tags[j] = tagJa.getString(j);
            }
            gi.simpleTags = tags;
            gi.pages = NumberUtils.parseIntSafely(ParserUtils.trim(g.getString("filecount")), 0);
            gi.generateSLang();
            stats.updated++;
        }

        // 请求了但站点压根没返回条目（已删除/被隐藏）的，也算缺失。
        stats.missing += Math.max(0, stats.requested - stats.returned);
        return stats;
    }

    private static GalleryInfo getGalleryInfoByGid(List<GalleryInfo> galleryInfoList, long gid) {
        for (int i = 0, size = galleryInfoList.size(); i < size; i++) {
            GalleryInfo gi = galleryInfoList.get(i);
            if (gi.gid == gid) {
                return gi;
            }
        }
        return null;
    }
}
