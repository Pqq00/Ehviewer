// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.proxy

/**
 * 代理设置落地。
 *
 * - enabled = false：跟随系统（Android 走系统代理/VPN，桌面走 JVM 默认 ProxySelector）
 * - enabled = true ：使用 host:port 的 HTTP 代理
 *
 * 调用后 [com.hippo.ehviewer.net.EhHttp] 会重建 OkHttp 客户端；
 * Cookie 存在 EhCookieJar 里，不受重建影响，登录态不会丢。
 */
expect fun applyProxy(type: String, host: String, port: Int)
