// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

import android.content.pm.ApplicationInfo
import app.eros.shared.settings.AndroidPlatform

actual val isDebugBuild: Boolean
    get() = runCatching {
        val info = AndroidPlatform.appContext.applicationInfo
        (info.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }.getOrDefault(true)