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

    /**
     * 域名是否属于某个站点域（含子域），按 DNS 标签边界判断：
     * `e-hentai.org` 与 `forums.e-hentai.org` 命中，`evile-hentai.org` 不命中。
     *
     * 不能用 {@code endsWith}：`.endsWith("e-hentai.org")` 会把 `not-e-hentai.org`
     * 这类域名也算成自家站点，cookie 和 Referer 就会泄漏给第三方。
     */
    public static boolean isDomainOrSubdomainOf(String host, String domain) {
        if (host == null || domain == null) {
            return false;
        }
        String h = host.toLowerCase(java.util.Locale.ROOT);
        String d = domain.toLowerCase(java.util.Locale.ROOT);
        if (h.equals(d)) {
            return true;
        }
        return h.length() > d.length()
                && h.endsWith("." + d);
    }

    /** 主站或里站（含子域，如 forums.e-hentai.org）。 */
    public static boolean isEhSite(String host) {
        return isDomainOrSubdomainOf(host, DOMAIN_E) || isDomainOrSubdomainOf(host, DOMAIN_EX);
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
