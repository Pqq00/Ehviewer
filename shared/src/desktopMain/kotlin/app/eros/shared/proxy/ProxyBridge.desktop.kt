// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.proxy

import com.hippo.ehviewer.net.EhHttp
import com.hippo.ehviewer.net.ProxyConfig

actual fun applyProxy(type: String, host: String, port: Int) {
    val valid = host.isNotBlank() && port in 1..65535
    val config = when (type) {
        "direct" -> ProxyConfig.Direct
        "http" -> if (valid) ProxyConfig.Http(host.trim(), port) else ProxyConfig.System
        "socks5" -> if (valid) ProxyConfig.Socks(host.trim(), port) else ProxyConfig.System
        else -> ProxyConfig.System
    }
    EhHttp.update(proxy = config)
    println("[Proxy] applied -> $config")
}
