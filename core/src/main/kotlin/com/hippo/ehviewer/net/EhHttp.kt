// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import com.hippo.ehviewer.client.EhUrl
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * 代理配置。
 *
 * - [System] 跟随系统：Android 走系统代理 / VPN，桌面走 JVM 默认 ProxySelector。
 * - [Http] 手动指定 HTTP 代理（例如本机的 Clash 127.0.0.1:7897）。
 */
sealed interface ProxyConfig {
    data object System : ProxyConfig

    data object Direct : ProxyConfig

    data class Http(val host: String, val port: Int) : ProxyConfig

    data class Socks(val host: String, val port: Int) : ProxyConfig
}

/**
 * 全局 HTTP 客户端。
 *
 * 相比上一版的传输层，这里补了 EhEngine 管道里最要紧的三件事：
 *  1. Referer —— e-hentai 的图床（H@H）会校验来源，不带就 403/509
 *  2. 重试 + 退避 —— 站点抖动时不要一次失败就报错
 *  3. Cookie —— 登录态、igneous
 */
object EhHttp {

    /** 默认 UA：与桌面浏览器一致，E-Hentai 对非浏览器 UA 更敏感。 */
    const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    @Volatile
    var proxy: ProxyConfig = ProxyConfig.System

    @Volatile
    var userAgent: String = DEFAULT_UA

    /** 同一 host 的最大并发请求数（OkHttp Dispatcher）。 */
    @Volatile
    var maxRequestsPerHost: Int = 6

    /** 全局请求速率上限（每秒，<=0 表示不限）。 */
    @Volatile
    var maxRequestsPerSecond: Int = 6

    /** 域名前置：把主站域名解析到固定 IP（仍保留原始 Host/SNI）。 */
    @Volatile
    var domainFronting: Boolean = false

    internal val rateGate = RateGate()

    @Volatile
    private var cached: OkHttpClient? = null

    val client: OkHttpClient
        get() = cached ?: synchronized(this) {
            cached ?: build().also { cached = it }
        }

    fun update(
        proxy: ProxyConfig? = null,
        userAgent: String? = null,
        maxRequestsPerHost: Int? = null,
        maxRequestsPerSecond: Int? = null,
        domainFronting: Boolean? = null,
    ) {
        if (proxy != null) this.proxy = proxy
        if (userAgent != null) this.userAgent = userAgent
        if (maxRequestsPerHost != null) {
            this.maxRequestsPerHost = maxRequestsPerHost.coerceIn(2, 8)
        }
        if (maxRequestsPerSecond != null) {
            this.maxRequestsPerSecond = maxRequestsPerSecond.coerceIn(0, 20)
        }
        if (domainFronting != null) {
            this.domainFronting = domainFronting
        }
        synchronized(this) { cached = null }
    }

    private fun build(): OkHttpClient {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = this@EhHttp.maxRequestsPerHost
        }
        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            // 整次调用的硬上限：DNS 挂住 / 假 IP 被黑洞时，连接超时可能不生效。
            .callTimeout(25, TimeUnit.SECONDS)
            .connectTimeout(6, TimeUnit.SECONDS)
            // 卡片页 HTML 常见几十 KB，30s 读超时会让一条卡死的 keep-alive 连接拖住整页 30s，
            // 12s 足够慢网读完，又能让 RetryInterceptor 快速换连接重试。
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .cookieJar(EhCookieJar)
            .dns(if (domainFronting) EhDns else Dns.SYSTEM)
            .addInterceptor(RefererInterceptor)
            .addInterceptor(HttpCacheInterceptor())
            .addInterceptor(RateLimitInterceptor())
            .addInterceptor(RetryInterceptor(maxRetries = 2))
            .followRedirects(true)
            .followSslRedirects(true)

        when (val p = proxy) {
            is ProxyConfig.System -> Unit
            is ProxyConfig.Direct -> builder.proxy(Proxy.NO_PROXY) // 交给默认 ProxySelector
            is ProxyConfig.Http -> builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(p.host, p.port)))
            is ProxyConfig.Socks -> builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(p.host, p.port)))
        }

        return builder.build()
    }

    /** 代理 / 域名前置的连通性探测（当前设置下请求 e-hentai.org）。 */
    fun testConnection(timeoutMs: Long = 8000): String {
        val started = System.currentTimeMillis()
        return try {
            val probe = client.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
            probe.newCall(
                Request.Builder().url("https://e-hentai.org/").head().build(),
            ).execute().use { response ->
                "连接成功 · HTTP ${response.code} · ${System.currentTimeMillis() - started} ms"
            }
        } catch (e: Exception) {
            "连接失败：${e.message ?: e::class.simpleName} · ${System.currentTimeMillis() - started} ms"
        }
    }

    /** 测试/调试用：把 "host:port" 解析成 [ProxyConfig.Http]。 */
    fun parseProxy(raw: String?): ProxyConfig? {
        if (raw.isNullOrBlank()) return null
        val idx = raw.lastIndexOf(':')
        if (idx <= 0) return null
        val host = raw.substring(0, idx)
        val port = raw.substring(idx + 1).toIntOrNull() ?: return null
        return ProxyConfig.Http(host, port)
    }
}

/**
 * 按目标站点补 Referer。
 *
 * - 论坛 / 主站：用自身域名
 * - 图床（ehgt.org、*.hath.network 等）：必须用 https://e-hentai.org/ 作为来源
 */
internal object RefererInterceptor : Interceptor {

    private val siteReferer: String
        get() = if (com.hippo.ehviewer.Settings.isExHentai) "https://exhentai.org/" else "https://e-hentai.org/"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Referer") != null) return chain.proceed(request)

        val host = request.url.host.lowercase()
        val referer = when {
            // 按 DNS 标签边界判断，避免把 not-e-hentai.org 当成主站。
            EhUrl.isEhSite(host) -> "https://$host/"
            else -> siteReferer // 图床用当前站点做来源（里站图必须带 exhentai 的 Referer）
        }
        return chain.proceed(request.newBuilder().header("Referer", referer).build())
    }
}

/**
 * 重试 + 指数退避，**网络层唯一的重试归属点**。
 *
 * - 只对 GET 生效：POST（收藏/评论/投票）重放会产生副作用；表单 POST 虽然
 *   通常幂等，但服务端并没有承诺，所以一样不重试。
 * - 只有 5xx / 429 / IO 异常重试；其它状态码原样交给上层分类。
 * - 429 优先用 `Retry-After`（数字与 HTTP-date 都认，见 [RetryAfter]），
 *   钳到 10 秒；没给或用 5xx 时用带抖动的本地退避。
 * - 调用方（例如 `PageBatchHarvester`）不得再叠一层重试：一次 503 会被
 *   放大成 3 × 3 次请求，正是之前「同一地址 GET 11–15 次」的来源之一。
 *
 * 每次重试都写进 [NetLog]，方便排查「到底重试了几次」。
 */
internal class RetryInterceptor(private val maxRetries: Int = 2) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 只重试幂等 GET：POST（收藏/评论/投票）重放会产生副作用。
        val idempotent = request.method == "GET"
        var attempt = 0

        while (true) {
            try {
                val response = chain.proceed(request)
                val retryable = idempotent && (response.code in 500..599 || response.code == 429)
                if (retryable && attempt < maxRetries) {
                    val wait = if (response.code == 429) {
                        RetryAfter.parseMillis(response.header("Retry-After"))
                            ?.coerceIn(0L, MAX_RETRY_AFTER_MS)
                    } else {
                        null
                    }
                    response.close()
                    attempt++
                    NetLog.log("RETRY", request.url.toString(), response.code, 0, 0, "第 $attempt 次重试")
                    sleepUnlessCanceled(chain, wait ?: backoffMillis(attempt))
                    continue
                }
                return response
            } catch (e: IOException) {
                // 调用方主动取消（Coil 滑出屏幕、页面退出、协程超时）不重试，否则会刷出大量 RETRY 日志与无效请求。
                if (chain.call().isCanceled() ||
                    e.message?.contains("Canceled", ignoreCase = true) == true ||
                    e.message?.contains("cancel", ignoreCase = true) == true
                ) {
                    throw e
                }
                if (!idempotent || attempt >= maxRetries) throw e
                attempt++
                NetLog.log("RETRY", request.url.toString(), -1, 0, 0, "第 $attempt 次重试（${e.message}）")
                sleepUnlessCanceled(chain, backoffMillis(attempt))
            }
        }
    }

    /**
     * 退避等待，但每 50 ms 查一次取消标志。
     *
     * 否则一次 1.5s 的退避睡完才轮到 API 层发现取消，等于把「3s 超时」拖成 4.5s。
     */
    private fun sleepUnlessCanceled(chain: Interceptor.Chain, millis: Long) {
        val deadline = System.nanoTime() + millis * 1_000_000
        while (true) {
            if (chain.call().isCanceled()) throw IOException("Canceled")
            val remainMs = (deadline - System.nanoTime()) / 1_000_000
            if (remainMs <= 0) return
            Thread.sleep(minOf(remainMs, 50L))
        }
    }

    /**
     * 本地指数退避：600 / 1500 / 3000 ms，各加 ±20% 抖动。
     *
     * 没有抖动时，同时失败的几个请求（首页列表 + 详情预取 + 下载）会在同一刻
     * 一起重试，把站点限速窗口又打满一次。
     */
    private fun backoffMillis(attempt: Int): Long {
        val base = when (attempt) {
            1 -> 600L
            2 -> 1500L
            else -> 3000L
        }
        return base + Random.nextLong(-base / 5, base / 5 + 1)
    }

    private companion object {
        /** `Retry-After` 最长等 10 秒：再长就该让用户看到错误，而不是挂住。 */
        const val MAX_RETRY_AFTER_MS = 10_000L
    }
}


/** 令牌桶：限制全局 API 请求速率，避免短时间打满站点。 */
internal class RateGate {
    private var nextAtNanos = 0L

    fun acquire() {
        val perSecond = EhHttp.maxRequestsPerSecond
        if (perSecond <= 0) return
        val interval = 1_000_000_000L / perSecond
        val waitNanos: Long
        synchronized(this) {
            val now = System.nanoTime()
            val at = maxOf(now, nextAtNanos)
            nextAtNanos = at + interval
            waitNanos = at - now
        }
        if (waitNanos > 0) {
            try {
                Thread.sleep(waitNanos / 1_000_000, (waitNanos % 1_000_000).toInt())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}

/** 对标注了 X-Eh-RateLimit 的请求做全局限速。 */
internal class RateLimitInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.request().header("X-Eh-RateLimit") != null) {
            EhHttp.rateGate.acquire()
        }
        return chain.proceed(chain.request())
    }
}

/**
 * 域名前置：把主站域名解析到固定 IP，绕开本地 DNS 污染；
 * URL/Host/SNI 均保持原域名，TLS 仍然有效。
 */
internal object EhDns : Dns {
    private val rotation = ConcurrentHashMap<String, AtomicInteger>()

    private val hostToIps = mapOf(
        "e-hentai.org" to listOf("172.66.132.196", "172.66.140.62"),
        "exhentai.org" to listOf(
            "178.175.128.251", "178.175.128.252", "178.175.128.253", "178.175.128.254",
            "178.175.129.251", "178.175.129.252", "178.175.129.253", "178.175.129.254",
            "178.175.132.19", "178.175.132.20", "178.175.132.21", "178.175.132.22",
        ),
        "upld.e-hentai.org" to listOf("95.211.208.236", "89.149.221.236"),
        "api.e-hentai.org" to listOf(
            "37.48.92.161", "212.7.202.51", "5.79.104.110", "37.48.81.204", "212.7.200.104",
        ),
        "forums.e-hentai.org" to listOf("172.66.132.196", "172.66.140.62"),
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val ips = hostToIps[hostname] ?: return Dns.SYSTEM.lookup(hostname)
        val start = rotation.getOrPut(hostname) { AtomicInteger(0) }.getAndIncrement()
        val n = ips.size
        return List(n) { i -> InetAddress.getByName(ips[(start % n + i) % n]) }
    }
}
