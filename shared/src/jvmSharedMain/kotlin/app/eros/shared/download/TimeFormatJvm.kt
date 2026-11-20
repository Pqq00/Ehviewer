// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.download

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

actual fun formatClockTime(epochMillis: Long): String =
    if (epochMillis <= 0L) "" else clockFormat.format(Date(epochMillis))
