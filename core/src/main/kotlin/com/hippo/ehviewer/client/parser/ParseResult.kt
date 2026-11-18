// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client.parser

import com.hippo.ehviewer.client.exception.ParseException

/**
 * 解析结果的显式形态：区分「解析成功、内容为空」和「解析器看不懂这个页面」。
 *
 * 旧写法（`catch (Throwable ignored) { return new Result(); }`）把这两种情况都压成
 * 一个空结果：页面上明明写着 Cloudflare 验证或登录失效，界面却只显示「没有内容」，
 * 排查时也无从下手。需要显式判断失败的分支用 [ParseResult]；只想拿结果的调用方
 * 仍可调各解析器的 `parse(...)`，失败时抛 [ParseException]。
 */
sealed interface ParseResult<out T> {

    /** 解析成功；[value] 可能是空列表（页面上确实没有条目），那不是失败。 */
    data class Ok<T>(val value: T) : ParseResult<T>

    /**
     * 解析失败。[message] 是给人看的原因，[body] 保留原始页面（截断由调用方决定），
     * [cause] 是底层异常，便于 netlog 里一带而过。
     */
    data class Err(
        val message: String,
        val body: String = "",
        val cause: Exception? = null,
    ) : ParseResult<Nothing>

    fun getOrNull(): T? = (this as? Ok)?.value

    /** 失败时抛 [ParseException]，成功后返回结果。 */
    fun getOrThrow(): T = when (this) {
        is Ok -> value
        is Err -> throw ParseException(message, body, cause)
    }

    fun <R> map(transform: (T) -> R): ParseResult<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }
}
