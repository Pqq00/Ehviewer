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

package com.hippo.ehviewer

/**
 * Minimal stand-in for EhViewer's 56KB Settings facade.
 *
 * Only the switches consumed by the ported client layer live here; they are plain
 * defaults until the UI layer wires a real preference store in.
 */
object Settings {

    /** 显示日文标题（有的话）。 */
    @JvmStatic
    var showJpnTitle: Boolean = false

    /** 缩略图分辨率：0 原图 / 1 250px / 2 300px。 */
    @JvmStatic
    var thumbResolution: Int = 0

    /** 使用里站（exhentai）。 */
    @JvmStatic
    var isExHentai: Boolean = false

    /** 修正缩略图 URL。 */
    @JvmStatic
    var fixThumbUrl: Boolean = false

    /** 画廊站点：0 = 表站, 1 = 里站（EhUrl.SITE_E / SITE_EX）。 */
    @JvmStatic
    var gallerySite: Int = 0
}