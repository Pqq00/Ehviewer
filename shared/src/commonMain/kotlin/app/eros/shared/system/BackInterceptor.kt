// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.system

/**
 * 接管系统返回。
 *
 * Android 15+ 的预测式返回是 OS 级 opt-in（targetSdk ≥ 35 默认开启），
 * 运行时没法通过 API 关闭「跟手预览」本身；但可以用一个**不带动画回调**的
 * [android.window.OnBackInvokedCallback] 以更高优先级接管返回，
 * 系统就不会再向应用索取跟手进度，从而表现为「瞬间返回」。
 *
 * - enabled = true：注册接管回调，系统返回直接走 [onBack]
 * - enabled = false：注销，交还给 miuix-nav 的预测式返回
 */
expect fun setPlainBackInterceptor(enabled: Boolean, onBack: (() -> Boolean)?)
