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

package com.hippo.ehviewer.client;

/**
 * Subset of EhViewer's EhUrl: the domain constants and API endpoints.
 * URL builders that depend on user settings will be ported together with EhEngine.
 */
public final class EhUrl {

    public static final String DOMAIN_EX = "exhentai.org";
    public static final String DOMAIN_E = "e-hentai.org";
    public static final String DOMAIN_LOFI = "lofi.e-hentai.org";

    public static final String REFERER_EX = "https://" + DOMAIN_EX;
    public static final String REFERER_E = "https://" + DOMAIN_E;
    public static final String HOST_EX = REFERER_EX + "/";
    public static final String HOST_E = REFERER_E + "/";

    public static final String API_SIGN_IN = "https://forums.e-hentai.org/index.php?act=Login&CODE=01";
    public static final String API_E = HOST_E + "api.php";
    public static final String API_EX = HOST_EX + "api.php";
    public static final String DOMAIN_FORUMS = "forums.e-hentai.org";
    public static final String URL_FORUMS = "https://forums.e-hentai.org/";
    public static final int SITE_E = 0;
    public static final int SITE_EX = 1;

    private EhUrl() {
    }

    public static String getApiUrl() {
        return com.hippo.ehviewer.Settings.isExHentai() ? API_EX : API_E;
    }

    /**
     * 上游会在这里把预览缩略图换成更清晰的地址；当前不做转换，原样返回。
     */
    public static String getFixedPreviewThumbUrl(String url) {
        return url;
    }
}
