package app.eros.shared.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 应用的全部导航目标。
 *
 * 从 `App.kt` 抽出（Phase 1 纯搬迁）：`App()`、窗口级 chrome 与各页面入口都要引用路由，
 * 定义留在 `App.kt` 里会让同一个文件同时承担路由、导航与布局三件事。
 */
@Serializable
internal sealed interface Route : NavKey {
    @Serializable
    data object Tabs : Route

    @Serializable
    data class Detail(val gid: Long, val token: String) : Route

    @Serializable
    data class Reader(
        val gid: Long,
        val token: String,
        val total: Int,
        val startPage: Int = -1,
    ) : Route

    @Serializable
    data class Comments(val gid: Long, val token: String) : Route

    @Serializable
    data object Search : Route

    @Serializable
    data object Account : Route

    @Serializable
    data object Watched : Route

    @Serializable
    data object NetLog : Route

    @Serializable
    data object Filter : Route

    /** 首页分类行条目的新建/编辑页；[key] 为空表示新建分组。 */
    data class HomeChipEdit(val key: String? = null) : Route

    @Serializable
    data object History : Route
}
