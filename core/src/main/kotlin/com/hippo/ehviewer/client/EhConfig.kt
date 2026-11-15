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

package com.hippo.ehviewer.client

/** 分类位标志（EhConfig 完整版依赖 dao/InetValidator，暂只保留这几位）。 */
object EhConfig {
    const val MISC = 0x1
    const val DOUJINSHI = 0x2
    const val MANGA = 0x4
    const val ARTIST_CG = 0x8
    const val GAME_CG = 0x10
    const val IMAGE_SET = 0x20
    const val COSPLAY = 0x40
    const val ASIAN_PORN = 0x80
    const val NON_H = 0x100
    const val WESTERN = 0x200
    const val UNKNOWN = 0x400

    /** 所有可见分类的位或（不含 UNKNOWN）。 */
    const val ALL_CATEGORY = MISC or DOUJINSHI or MANGA or ARTIST_CG or GAME_CG or IMAGE_SET or COSPLAY or ASIAN_PORN or NON_H or WESTERN

    const val KEY_CONTENT_WARNING = "nw"
    const val CONTENT_WARNING_NOT_SHOW = "1"

    const val MODE_NORMAL = 0
    const val MODE_UPLOADER = 1
    const val MODE_TAG = 2
    const val MODE_WATCHED = 3
    const val MODE_IMAGE_SEARCH = 4
}