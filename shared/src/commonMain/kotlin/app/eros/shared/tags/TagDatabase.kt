// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.tags

import androidx.compose.runtime.getValue
import com.hippo.ehviewer.util.AppLog
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 标签库的懒加载入口。
 *
 * 词库约 8.8 万条，解析要几百毫秒（有本地索引时 ~100ms，见 `TagDatabaseBridge`）。
 * 以前在 `App()` 启动时同步阻塞（虽然跑在 IO 线程，但与首屏请求争抢线程，
 * 且把 `restoreAuth()` 排在它后面），所以改成后台预热。
 *
 * 现在：
 *  - 启动阶段只做网络/登录恢复，标签库**立刻**在后台预热（不再固定延迟 1.5s：
 *    解析只占一个 IO 线程几百毫秒，而首屏网络请求要 1~18s，根本不存在争抢）
 *  - 用到翻译的地方（详情页）await [ensureLoaded]，通常预热已完成，无感
 *  - 设置页更新词库后调用 [reload] 重新载入
 */
object TagDatabase {

    private val mutex = Mutex()
    private val ready = CompletableDeferred<Int>()

    /** Compose 可观察的版本号：每次载入/重载后自增，供卡片等订阅刷新。 */
    var revision by mutableIntStateOf(0)
        private set

    /** 是否已载入（可能为 0 条） */
    val isLoaded: Boolean get() = ready.isCompleted

    val loadedCount: Int get() = if (ready.isCompleted) ready.getCompleted() else 0

    /** 挂起直到词库载入完成；不会重复加载。 */
    suspend fun ensureLoaded(): Int {
        if (ready.isCompleted) return ready.await()
        mutex.withLock {
            if (ready.isCompleted) return ready.await()
            val started = System.currentTimeMillis()
            val count = withContext(Dispatchers.IO) { initTagDatabase() }
            ready.complete(count)
            revision++
            AppLog.i("TagDB", "loaded $count in ${System.currentTimeMillis() - started}ms")
            return count
        }
    }

    /** 重新从磁盘载入（设置页更新词库后调用）。 */
    suspend fun reload(): Int = mutex.withLock {
        val started = System.currentTimeMillis()
        val count = withContext(Dispatchers.IO) { initTagDatabase() }
        if (!ready.isCompleted) ready.complete(count)
        revision++
        AppLog.i("TagDB", "reloaded $count in ${System.currentTimeMillis() - started}ms")
        count
    }

    /** 后台预热：默认立刻开始，避免「先英文再变中文」的闪动。 */
    fun warmUpIn(scope: CoroutineScope, delayMs: Long = 0) {
        if (ready.isCompleted) return
        scope.launch {
            AppLog.d("TagDB", "warmup scheduled in ${delayMs}ms")
            delay(delayMs)
            runCatching { ensureLoaded() }.onFailure {
                AppLog.w("TagDB", "warmup FAILED: ${it.message}")
            }
        }
    }
}
