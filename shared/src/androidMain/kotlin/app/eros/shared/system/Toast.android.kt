// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.system

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.eros.shared.settings.AndroidPlatform

actual fun showSystemToast(message: String) {
    Handler(Looper.getMainLooper()).post {
        runCatching {
            Toast.makeText(AndroidPlatform.appContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
