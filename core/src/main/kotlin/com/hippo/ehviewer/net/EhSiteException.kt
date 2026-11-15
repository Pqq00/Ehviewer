// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import java.io.IOException

/** 站点语义错误的种类；上层按它决定提示文案与是否重试。 */
enum class EhSiteErrorKind {
    /** 响应体为空（可能被中间设备拦断）。 */
    BLANK_BODY,

    /** 被站点封禁。 */
    BANNED,

    /** 撞上 Cloudflare 挑战页。 */
    CLOUDFLARE,

    /** gdata 确认画廊已删除。 */
    GALLERY_DELETED,

    /** 画廊页打不开，但 gdata 也没确认删除（隐藏、需登录或网络问题）。 */
    GALLERY_UNAVAILABLE,

    /** 请求超时。 */
    TIMEOUT,
}

/**
 * 站点语义错误：请求本身成功，但返回的内容说明这次操作不该被当作正常结果。
 *
 * 与 [HttpStatusException] 的区别是它关心的是「页面内容说明了什么」，
 * 而不是 HTTP 状态码。
 */
class EhSiteException(
    val kind: EhSiteErrorKind,
    override val message: String,
    val url: String? = null,
) : IOException(message)
