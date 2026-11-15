// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.download

/** 格式化完成时间为本地时钟（HH:mm）。 */
expect fun formatClockTime(epochMillis: Long): String
