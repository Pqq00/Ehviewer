// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import app.eros.shared.settings.AppSettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.eros.shared.proxy.applyProxy
import com.hippo.ehviewer.net.EhHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置页「网络」一节：代理、并发与限速、固定 IP 解析。 */
@Composable
internal fun SettingsNetworkSection(
    scope: CoroutineScope,
    proxyHost: TextFieldState,
    proxyPort: TextFieldState,
    onMessage: (String) -> Unit,
) {
    var proxyType by remember { mutableStateOf(AppSettings.proxyType) }
    ErosDropdownPreference(
        title = "代理类型",
        items = listOf("跟随系统", "HTTP", "SOCKS5", "直连"),
        selectedIndex = when (proxyType) {
            "http" -> 1
            "socks5" -> 2
            "direct" -> 3
            else -> 0
        },
        onSelectedIndexChange = { index ->
            val type = listOf("system", "http", "socks5", "direct")[index]
            proxyType = type
            AppSettings.proxyType = type
            applyProxy(type, AppSettings.proxyHost, AppSettings.proxyPort)
            onMessage(
                when (type) {
                    "http" -> "代理：HTTP ${AppSettings.proxyHost}:${AppSettings.proxyPort}"
                    "socks5" -> "代理：SOCKS5 ${AppSettings.proxyHost}:${AppSettings.proxyPort}"
                    "direct" -> "代理：直连"
                    else -> "代理：跟随系统"
                },
            )
        },
    )
    if (proxyType == "http" || proxyType == "socks5") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(state = proxyHost, label = "代理主机（如 127.0.0.1）")
            TextField(state = proxyPort, label = "端口（如 7890）")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ActionText("应用代理") {
                    val host = proxyHost.text.toString().trim()
                    val port = proxyPort.text.toString().trim().toIntOrNull()
                    if (host.isBlank() || port == null || port !in 1..65535) {
                        onMessage("代理地址不合法")
                    } else {
                        AppSettings.proxyHost = host
                        AppSettings.proxyPort = port
                        applyProxy(proxyType, host, port)
                        onMessage("已应用代理 $host:$port")
                    }
                }
            }
        }
    }
    ErosDropdownPreference(
        title = "同主机并发数",
        items = listOf("2", "4", "6", "8"),
        selectedIndex = when (AppSettings.netMaxPerHost) {
            2 -> 0
            4 -> 1
            8 -> 3
            else -> 2
        },
        onSelectedIndexChange = { index ->
            val n = listOf(2, 4, 6, 8)[index]
            AppSettings.netMaxPerHost = n
            EhHttp.update(maxRequestsPerHost = n)
            onMessage("同主机并发数：$n")
        },
    )
    ErosDropdownPreference(
        title = "全局限速",
        summary = "过高会被限流",
        items = listOf("不限", "4 次/秒", "6 次/秒", "8 次/秒", "12 次/秒"),
        selectedIndex = when (AppSettings.maxRequestsPerSecond) {
            0 -> 0
            4 -> 1
            8 -> 3
            12 -> 4
            else -> 2
        },
        onSelectedIndexChange = { index ->
            val n = listOf(0, 4, 6, 8, 12)[index]
            AppSettings.maxRequestsPerSecond = n
            EhHttp.update(maxRequestsPerSecond = n)
            onMessage(if (n == 0) "全局限速：不限" else "全局限速：$n 次/秒")
        },
    )
    SwitchPreference(
        title = "固定 IP 解析",
        summary = "绕开 DNS 污染",
        checked = AppSettings.domainFronting,
        onCheckedChange = { enabled ->
            AppSettings.domainFronting = enabled
            EhHttp.update(domainFronting = enabled)
            if (!enabled) {
                onMessage("域名前置：关")
            } else {
                onMessage("正在检测域名前置…")
                scope.launch {
                    val result = withContext(Dispatchers.IO) { EhHttp.testConnection(6000) }
                    if (result.startsWith("连接失败")) {
                        AppSettings.domainFronting = false
                        EhHttp.update(domainFronting = false)
                    }
                    onMessage("域名前置：$result")
                }
            }
        },
    )
}

/** 卡内动作按钮：用 miuix 官方 Button（次要色 + 标准按压反馈）。 */
@Composable
private fun ActionText(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        minHeight = 34.dp,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 14.dp,
            vertical = 6.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MiuixTheme.colorScheme.primary,
        ),
    ) {
        Text(label, style = MiuixTheme.textStyles.button)
    }
}
