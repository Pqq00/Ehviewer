package app.eros.shared.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack

/**
 * 应用内唯一的一级导航器（Phase 1 从 `App()` 抽出的 push/pop 逻辑）。
 *
 * 只暴露「推、退、退回 Tabs」三种动作与只读状态；转场开关、返回拦截仍由 `App()` 负责，
 * 因为它们与导航动画、系统返回事件绑定。
 */
@Stable
internal class ErosNavigator(val backStack: NavBackStack) {

    /** 当前栈顶路由（栈底永远是 [Route.Tabs]，所以不为空）。 */
    val current: Route? get() = backStack.lastOrNull() as? Route

    /** 当前是否停在详情页。 */
    val detail: Route.Detail? get() = current as? Route.Detail

    val canPop: Boolean get() = backStack.size > 1

    /**
     * 推入一个路由；若栈里已有同款路由，则退回那一层而不是再叠一层
     * （例如从详情页再点同一个画廊）。
     */
    fun push(route: Route) {
        val existing = backStack.indexOfLast { it == route }
        if (existing >= 0) {
            while (backStack.size - 1 > existing) backStack.removeLastOrNull()
        } else {
            backStack.add(route)
        }
    }

    /** 退回上一页；栈底 [Route.Tabs] 不会被弹出。 */
    fun pop() {
        if (canPop) backStack.removeLastOrNull()
    }

    /** 一路退到 Tabs 页（切换主导航项时用）。 */
    fun popToTabs() {
        while (backStack.size > 1) backStack.removeLastOrNull()
    }
}

@Composable
internal fun rememberErosNavigator(initial: Route = Route.Tabs): ErosNavigator {
    val backStack = rememberNavBackStack<Route>(initial)
    return remember(backStack) { ErosNavigator(backStack) }
}
