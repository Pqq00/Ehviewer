// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

import android.util.Log
import androidx.compose.runtime.Composable
import coil3.EventListener
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.hippo.ehviewer.net.EhHttp
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/**
 * 封面加载专用的 OkHttp 客户端。
 *
 * 与 [EhHttp.client] 共享拦截器（Cookie / Referer / UA / 代理），并发放到 16/8，
 * 避免信息流/榜单一次挂 50 张封面时打满站点（509 或超时）。
 * client 被重建（如代理变化）时这里跟着重建。
 */
private var coilBase: OkHttpClient? = null
private var coilClient: OkHttpClient? = null

private fun coverClient(): OkHttpClient {
    val base = EhHttp.client
    if (coilBase !== base || coilClient == null) {
        coilClient = base.newBuilder()
            .dispatcher(Dispatcher().apply { maxRequests = 24; maxRequestsPerHost = 12 })
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
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .eventListener(object : EventListener() {
                private val starts = java.util.concurrent.ConcurrentHashMap<String, Long>()

                override fun onStart(request: ImageRequest) {
                    starts[request.data.toString()] = System.currentTimeMillis()
                }

                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    val key = request.data.toString()
                    val start = starts.remove(key) ?: return
                    Log.i("CoilTiming", "ok ${System.currentTimeMillis() - start}ms $key")
                }

                override fun onError(request: ImageRequest, result: ErrorResult) {
                    val key = request.data.toString()
                    val start = starts.remove(key) ?: return
                    Log.i("CoilTiming", "err ${System.currentTimeMillis() - start}ms $key ${result.throwable.message}")
                }
            })
            .build()
    }
}
