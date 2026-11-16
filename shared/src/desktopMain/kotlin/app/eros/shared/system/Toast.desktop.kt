// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.system

import com.hippo.ehviewer.util.AppLog

actual fun showSystemToast(message: String) {
    AppLog.i("Toast", message)
}
