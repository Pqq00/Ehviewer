// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.hippo.ehviewer.net.EhHttp
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

/**
 * 封面加载专用的 OkHttp 客户端。
 *
 * 与 [EhHttp.client] 共享拦截器（Cookie / Referer / UA / 代理），只把并发压到 4，
 * 避免信息流/榜单一次挂 50 张封面时打满站点（509 或超时）。
 * client 被重建（如代理变化）时这里跟着重建。
 */
private var coilBase: OkHttpClient? = null
private var coilClient: OkHttpClient? = null

private fun coverClient(): OkHttpClient {
    val base = EhHttp.client
    if (coilBase !== base || coilClient == null) {
        coilClient = base.newBuilder()
            .dispatcher(Dispatcher().apply { maxRequests = 8; maxRequestsPerHost = 4 })
            .build()
        coilBase = base
    }
    return coilClient!!
}

@Composable
actual fun SetupImageLoader() {
    // callFactory 是 lambda：代理/UA 变了之后会拿到新 client
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { coverClient() }))
            }
            .crossfade(false)
            .build()
    }
}
