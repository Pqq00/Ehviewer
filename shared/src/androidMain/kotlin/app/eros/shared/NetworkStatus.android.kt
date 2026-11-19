// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.eros.shared.settings.AndroidPlatform

actual fun isMeteredNetwork(): Boolean = runCatching {
    val manager = AndroidPlatform.appContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}.getOrDefault(false)
