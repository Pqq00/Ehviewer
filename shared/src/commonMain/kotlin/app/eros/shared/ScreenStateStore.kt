// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared

/**
 * 页面状态仓。
 *
 * 背景：Compose 里 `when (route)` 的分支一旦切换，原来的组合就被销毁，`remember` 的东西
 * 全部作废。于是「详情 → 阅读器 → 返回」会让详情页重新走一次网络请求。
 *
 * 规则（新加页面必须遵守）：
 *   1. 页面的数据状态写成一个 `XxxState` 类，构造时只存参数，不发起请求；
 *   2. 状态类自己维护 `loadedOnce`，重复进入时 [ensureLoaded] 直接返回；
 *   3. 状态实例从这里按 key 取，key 必须能唯一标识内容（如 `detail:<gid>:<token>`）；
 *   4. Composable 只接受 state 参数，不再自己 `remember` 数据。
 *
 * 缓存策略：**访问序 LRU，最多 [DEFAULT_MAX_ENTRIES] 个**。
 * 超出时淘汰最久未访问的条目（其页面若还在前台，组合仍持有引用，不受影响；
 * 再次进入会重新加载）。
 */
class ScreenStateStore(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {

    private val lock = Any()

    private val cache = object : LinkedHashMap<String, Any>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>): Boolean {
            val evict = size > maxEntries
            if (evict) println("[Store] evict ${eldest.key} (size=$size)")
            return evict
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: String, factory: () -> T): T = synchronized(lock) {
        cache.getOrPut(key, factory) as T
    }

    /** 当前缓存条目数（调试用）。 */
    val size: Int get() = synchronized(lock) { cache.size }

    fun clear() = synchronized(lock) { cache.clear() }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 24
    }
}