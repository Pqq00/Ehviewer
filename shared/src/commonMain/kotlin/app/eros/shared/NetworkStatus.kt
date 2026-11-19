// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

/**
 * 当前网络是否按流量计费（移动数据、热点等）。
 *
 * 未知时返回 false：宁可让预取照常进行，也不要在判断不了的情况下平白关掉用户预期的行为。
 * 设置项 `prefetchWifiOnly` 打开后，封面/详情预取在计费网络下直接跳过。
 */
expect fun isMeteredNetwork(): Boolean
