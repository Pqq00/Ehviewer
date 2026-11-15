// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

import androidx.compose.runtime.Composable

/**
 * 配置 Coil 使用我们自己的 OkHttp 客户端。
 *
 * 默认 Coil 会自建一个干净的 OkHttp：没有 cookie、没有 Referer、没有重试。
 * 表站图片无所谓，但**里站（exhentai）的图片必须带 igneous cookie 和 exhentai 的 Referer**，
 * 否则就是一片空白。
 */
@Composable
expect fun SetupImageLoader()
