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

package com.hippo.ehviewer;

/**
 * 数据库门面的占位实现：本地收藏与黑名单都还没有落盘，查询一律返回「没有」。
 */
public final class EhDB {

    private EhDB() {
    }

    /**
     * 本地收藏表尚未迁入；当前恒为 false。调用方会据此把 favoriteSlot 算成 -2
     * （界面暂未使用该字段）。
     */
    public static boolean containLocalFavorites(long gid) {
        return false;
    }

    /** 黑名单过滤：当前没有黑名单数据，返回 false（不过滤）。 */
    public static boolean inBlackList(String user) {
        return false;
    }
}
