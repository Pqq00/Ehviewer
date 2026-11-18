// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.exception

import java.io.IOException

/**
 * 站点返回了非 2xx 状态码。
 *
 * 早先各请求点只能抛 `IOException("HTTP 429 ...")`，上层要判断错误类型就得匹配字符串。
 * 这里把状态码、`Retry-After`、请求地址显式带出来，`EhError` 可以直接按
 * 429 限流 / 403 拦截 / 5xx 服务端错误分类。
 */
class HttpStatusException(
    val code: Int,
    /**
     * 服务端的 `Retry-After`（秒）。
     *
     * 数字与 HTTP-date 两种形式都由 `com.hippo.ehviewer.net.RetryAfter` 解析后传入；
     * 日期形式换算成「距现在还有几秒」，已过期按 0。
     */
    val retryAfterSeconds: Long? = null,
    url: String? = null,
) : IOException(describe(code, url))

private fun describe(code: Int, url: String?): String =
    if (url.isNullOrBlank()) "HTTP $code" else "HTTP $code for $url"
