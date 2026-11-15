// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.data

import kotlinx.coroutines.CancellationException

/**
 * 统一封装 Eh 调用：异常转成用户可读文案，其余交给调用方。
 *
 * 约定：
 *  - 成功返回结果；
 *  - 失败把文案交给 [onError] 并返回 null；
 *  - 协程取消照常抛出（不吞掉）。
 *
 * 用法：
 * ```
 * val page = withEhErrors({ error = it }) { source.rankList(tl, 0) } ?: return
 * ```
 */
suspend fun <T> withEhErrors(
    onError: (String) -> Unit,
    block: suspend () -> T,
): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    onError(t.toEhError().userMessage())
    null
}

/**
 * [withEhErrors] 的类型化版本：把整个 [EhError] 交给调用方。
 *
 * 状态层需要区分"可重试/不可重试""删除/隐藏/维护"，只有文案是不够的。
 */
suspend fun <T> withEhError(
    onError: (EhError) -> Unit,
    block: suspend () -> T,
): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    onError(t.toEhError())
    null
}
