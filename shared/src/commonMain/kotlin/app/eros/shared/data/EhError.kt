// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

import com.hippo.ehviewer.client.exception.EmptyGalleryException
import com.hippo.ehviewer.client.exception.EhException
import com.hippo.ehviewer.client.exception.GalleryUnavailableException
import com.hippo.ehviewer.client.exception.HttpStatusException
import com.hippo.ehviewer.client.exception.Image509Exception
import com.hippo.ehviewer.client.exception.NoHAtHClientException
import com.hippo.ehviewer.client.exception.OffensiveException
import com.hippo.ehviewer.client.exception.ParseException
import com.hippo.ehviewer.client.exception.PiningException
import com.hippo.ehviewer.net.EhSiteErrorKind
import com.hippo.ehviewer.net.EhSiteException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 统一错误模型。
 *
 * `EhApi` 仍以异常形式抛出（改动最小），由 [toEhError] 在状态层统一翻译成可分类的错误，
 * UI 再按类型给出不同提示（重登/换源/重试/限额），而不是统一一句"加载失败"。
 */
sealed interface EhError {

    /** Sad Panda：站点返回了"没有内容"的页面（通常是配额耗尽或域名被墙）。 */
    data object SadPanda : EhError

    /** 图片配额耗尽（509）。 */
    data object ImageLimit : EhError

    /** 需要重新登录（igneous 失效、未登录）。 */
    data object NeedLogin : EhError

    /** 画廊已被删除：站点（以及 gdata 复核）都说找不到它了。重试没有意义。 */
    data object GalleryDeleted : EhError

    /** 画廊还在，但当前不可用（被隐藏 / 需要登录 / 地区限制）。重试没有意义。 */
    data object GalleryUnavailable : EhError

    /** 画廊正在维护（`pining for the fjords`），通常过一会儿会恢复。 */
    data object GalleryPining : EhError

    /** 请求被限流（HTTP 429）；[retryAfterSeconds] 来自 `Retry-After`，可能为空。 */
    data class RateLimited(val retryAfterSeconds: Long? = null) : EhError

    /** 被站点拦截（HTTP 403 / Cloudflare 挑战页）。 */
    data object Forbidden : EhError

    /**
     * 页面结构变化：HTTP 200 但解析失败。
     *
     * 与 [Http] 区分开——后者是状态码错误，前者是"站点改版/返回了登录墙页面"，
     * 重试没有意义，应提示用户更新或反馈。
     */
    data class SiteChanged(val detail: String, val body: String? = null) : EhError

    /** HTTP 状态码错误（429/403 之外的 4xx/5xx）。 */
    data class Http(val code: Int) : EhError

    /** 网络层错误（超时、DNS、连接失败）。 */
    data class Network(val cause: Throwable) : EhError

    /** 未归类。 */
    data class Unknown(val cause: Throwable) : EhError
}

/**
 * 异常 → [EhError]。
 *
 * 站点语义异常优先按类型判断，其次退化为消息匹配（core 层的 Java 代码有些地方只抛 `EhException`）。
 */
fun Throwable.toEhError(): EhError {
    val root = this
    return when {
        root is EhSiteException -> when (root.kind) {
            EhSiteErrorKind.BLANK_BODY -> EhError.SadPanda
            EhSiteErrorKind.BANNED -> EhError.Forbidden
            EhSiteErrorKind.CLOUDFLARE -> EhError.Forbidden
            EhSiteErrorKind.GALLERY_DELETED -> EhError.GalleryDeleted
            EhSiteErrorKind.GALLERY_UNAVAILABLE -> EhError.GalleryUnavailable
            EhSiteErrorKind.TIMEOUT -> EhError.Network(root)
        }
        root is Image509Exception -> EhError.ImageLimit
        root is GalleryUnavailableException -> EhError.GalleryUnavailable
        root is PiningException -> EhError.GalleryPining
        root is OffensiveException -> EhError.SadPanda
        root is EmptyGalleryException -> EhError.SadPanda
        root is NoHAtHClientException -> EhError.Network(root)
        root is HttpStatusException -> root.toEhError()
        root is ParseException -> EhError.SiteChanged(root.message ?: "解析失败", root.body.take(2048))
        root is SocketTimeoutException -> EhError.Network(root)
        root is UnknownHostException -> EhError.Network(root)
        root is IOException -> root.httpCodeFromMessage()?.toEhError() ?: EhError.Network(root)
        root is EhException -> classifyByMessage(root)
        else -> EhError.Unknown(root)
    }
}

private fun HttpStatusException.toEhError(): EhError = when (code) {
    429 -> EhError.RateLimited(retryAfterSeconds)
    403 -> EhError.Forbidden
    else -> EhError.Http(code)
}

/** 兼容旧的 `IOException("HTTP 429 ...")` 抛法。 */
private fun IOException.httpCodeFromMessage(): HttpStatusException? {
    val m = message ?: return null
    val match = Regex("""HTTP\s+(\d{3})""").find(m) ?: return null
    val code = match.groupValues[1].toIntOrNull() ?: return null
    return HttpStatusException(code)
}

private fun classifyByMessage(e: EhException): EhError {
    val message = e.message.orEmpty()
    val lower = message.lowercase()
    return when {
        lower.contains("509") || lower.contains("exceeded") -> EhError.ImageLimit
        lower.contains("pining for the fjords") -> EhError.GalleryPining
        lower.contains("sad panda") -> EhError.SadPanda
        // 只有明确的"删除"措辞才算删除；裸 "unavailable" 不算（那是隐藏/需登录，画廊还在）。
        lower.contains("gallery not found") ||
            lower.contains("has been removed") ||
            lower.contains("画廊已删除") -> EhError.GalleryDeleted
        lower.contains("this gallery is unavailable") -> EhError.GalleryUnavailable
        lower.contains("igneous") || lower.contains("not logged in") || lower.contains("login") ->
            EhError.NeedLogin
        else -> EhError.Unknown(e)
    }
}

/** 面向用户的短文案（不含敏感细节）。 */
fun EhError.userMessage(): String = when (this) {
    EhError.SadPanda -> "站点没有返回内容（配额可能已用完）"
    EhError.ImageLimit -> "图片配额已用完，请稍后重试"
    EhError.NeedLogin -> "登录已失效，请重新登录"
    EhError.GalleryDeleted -> "该画廊已被删除，站内已找不到"
    EhError.GalleryUnavailable -> "该画廊当前不可用（可能被隐藏或需要登录）"
    EhError.GalleryPining -> "站点正在维护，请稍后重试"
    is EhError.RateLimited ->
        retryAfterSeconds?.let { "请求过快，请 $it 秒后再试" } ?: "请求过快，请稍后再试"
    EhError.Forbidden -> "被站点拦截，请更换网络或代理"
    is EhError.SiteChanged -> "页面结构可能已变化：$detail"
    is EhError.Http -> "服务器返回 HTTP $code"
    is EhError.Network -> "网络异常：${cause.message ?: cause.javaClass.simpleName}"
    is EhError.Unknown -> cause.message ?: cause.javaClass.simpleName
}

/** 是否值得让用户点"重试"。 */
fun EhError.isRetryable(): Boolean = when (this) {
    EhError.SadPanda, EhError.ImageLimit -> true
    EhError.NeedLogin -> false
    EhError.GalleryDeleted -> false
    EhError.GalleryUnavailable -> false
    EhError.GalleryPining -> true
    is EhError.RateLimited -> true
    EhError.Forbidden -> false
    is EhError.SiteChanged -> false
    is EhError.Http -> code >= 500
    is EhError.Network -> true
    is EhError.Unknown -> true
}
