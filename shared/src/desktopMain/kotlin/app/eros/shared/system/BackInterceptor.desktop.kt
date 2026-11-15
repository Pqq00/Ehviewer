// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.system

/** 桌面端没有系统返回手势，空实现。 */
actual fun setPlainBackInterceptor(enabled: Boolean, onBack: (() -> Boolean)?) {
    // no-op
}
