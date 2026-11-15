// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.data

/**
 * 站点上的一套 Profile（`uconfig.php` 里的配置档案）。
 *
 * 站点允许存多套显示/过滤偏好（缩略图尺寸、首页布局、日文标题优先、tag 过滤、
 * 多页查看器……），每套有编号。当前用哪套由 cookie `sp` 决定；切档案只影响之后
 * 取到的页面，不改服务端数据。
 */
class SiteProfile(
    /** 档案编号，即 cookie `sp` 的值。 */
    val number: Int,
    /** 站点上显示的档案名。 */
    val name: String,
    /** 是否当前生效。 */
    val selected: Boolean,
)
