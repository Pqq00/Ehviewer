// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

import androidx.compose.foundation.lazy.grid.LazyGridState
import app.eros.shared.ScreenStateStore

/**
 * 进程级页面状态持有者。
 *
 * 之前这些状态放在 `App()` 的 `remember` 里：只要 Activity 被重建
 * （返回桌面待一会、系统回收 Activity、深色模式切换等），
 * 首页和收藏页的状态就会重置，表现为"明明不是冷启动，却重新加载一遍"。
 *
 * 放到进程级 object 后，Activity 重建不会丢数据；进程被杀才会丢（那是真冷启动）。
 */
object AppStateHolder {
    val store = ScreenStateStore()

    /** 当前 tab 下标：进程级持有，Activity 重建后回到原来那一页。 */
    var selectedTab by mutableIntStateOf(0)

    val homeGridState = LazyGridState()

    /** 排行榜页（独立 tab）。 */
    val topListState = TopListState()
    val rankGridState = LazyGridState()
    val favGridState = LazyGridState()

    val homeState: HomeState by lazy { store.get("home") { HomeState() } }
    val favouritesState: FavouritesState by lazy { store.get("favourites") { FavouritesState() } }
    val searchState: SearchState by lazy { store.get("search") { SearchState() } }
}
