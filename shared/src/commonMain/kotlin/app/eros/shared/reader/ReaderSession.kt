// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.eros.shared.data.EhApi
import app.eros.shared.data.cache.GalleryCache
import app.eros.shared.data.createEhApi
import app.eros.shared.settings.AppSettings
import app.eros.shared.settings.platformGetInt
import app.eros.shared.settings.platformSetInt
import app.eros.shared.ui.ReadStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 阅读器交互状态：页码、模式、顶底栏、弹层。缩放在 UI 层按页持有（[PageZoomState]）。
 *
 * 这里**不做任何下载/解码 IO**（那些在 [ReaderLoader]），只有阅读进度与浏览历史
 * 两条轻量本地写入，因此可以在重组之间长期持有，也可以在预览/测试里构造。
 */
class ReaderSession(
    val gid: Long,
    val token: String,
    galleryPages: Int = 0,
    startPage: Int = -1,
    api: EhApi = createEhApi(),
) {

    /** 页面数据的 IO 层。 */
    val loader = ReaderLoader(gid = gid, token = token, galleryPages = galleryPages, api = api)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val keyPage = "reader_page_$gid"
    private val keyMode get() = if (AppSettings.readerPerGalleryMode) "reader_mode_$gid" else "reader_mode"

    /** 需要给用户看的短提示（Snackbar）。 */
    val messages = Channel<String>(Channel.BUFFERED)

    /**
     * 立即覆盖上一条的短提示：连点切换翻页模式/图片适应时用。
     *
     * [messages] 是排队逐条播放的，连点会积压成好几秒的延迟；这类"当前状态"提示
     * 只关心最后一条，所以走单独的状态，由 UI 直接覆盖显示。
     */
    var transientHint by mutableStateOf<String?>(null)
        private set

    val totalPages: Int get() = loader.totalPages

    var currentPage by mutableStateOf(0)
        private set

    var mode by mutableStateOf(ReaderMode.LTR)
        private set

    var chromeVisible by mutableStateOf(true)
        private set

    var sheet by mutableStateOf(ReaderSheet.None)
        private set

    private var initialized = false
    private var chromeJob: Job? = null
    private var interaction by mutableIntStateOf(0)

    /**
     * 构造期传入的起始页（从「预览图」或「历史记录」直接进阅读器时用）。
     *
     * [ensureLoaded] 的 `startPage` 参数优先，没有才回落到这里；两者都为 -1 时
     * 才恢复「上次阅读页」。以前这个参数被直接丢掉，导致点预览图永远回到旧页。
     */
    private val initialPage = startPage

    /** 历史记录写库的节流任务：连续翻页只落最后一次，避免每页一次 SQLite 写。 */
    private var historyJob: Job? = null
    private var lastHistoryPage = -1

    // ------------------------------------------------------------------ 生命周期

    /** 首次进入页面时调用：恢复进度并开始装载。 */
    fun ensureLoaded(startPage: Int = -1) {
        if (initialized) return
        initialized = true
        restore(if (startPage >= 0) startPage else initialPage)
        scope.launch { ReadStore.recordProgress(gid, currentPage, totalPages) }
        recordHistory(currentPage)
        loader.ensureLoaded(currentPage)
        loader.requestPage(currentPage)
        scheduleChromeHide()
    }

    /**
     * 再次进入同一画廊时把会话挪到指定页。
     *
     * 会话按「画廊」缓存在 App 的 store 里（见 `entry<Route.Reader>`），退出阅读器再点预览图
     * 复用的是同一个实例：[ensureLoaded] 早就跑过、`startPage` 也不会重新生效，
     * 所以这里必须显式跳一次，否则会停在上次读到的那页。
     */
    fun openAt(index: Int) {
        ensureLoaded()
        goTo(index)
    }

    private fun restore(startPage: Int) {
        currentPage = if (startPage >= 0) {
            startPage
        } else {
            platformGetInt(keyPage, 0).coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        }
        // 从预览图/历史记录指定的页进来时，把它当成新的「上次阅读页」，退出再进才会停在原地。
        if (startPage >= 0) platformSetInt(keyPage, currentPage)
        mode = ReaderMode.fromOrdinal(platformGetInt(keyMode, 0))
        println("[Reader] restore page=$currentPage mode=${mode.label} total=$totalPages")
    }

    /**
     * 把当前页码写进浏览历史（`history` 表）。
     *
     * 之前只有「打开详情页」会写历史且页码恒为 0，历史列表因此永远显示「第 1 / N 页」。
     * 这里用 300ms 防抖合并连续翻页。
     */
    private fun recordHistory(page: Int) {
        val pages = totalPages
        if (pages <= 0) return
        if (page == lastHistoryPage) return
        lastHistoryPage = page
        historyJob?.cancel()
        historyJob = scope.launch {
            delay(300)
            runCatching { GalleryCache.recordHistory(gid, token, page, pages) }
                .onFailure { println("[Reader] recordHistory failed: " + it.message) }
        }
    }

    // ------------------------------------------------------------------ 翻页

    /** 跳到指定页（会 clamp 并持久化）。 */
    fun goTo(index: Int, animatedHint: Boolean = false) {
        val target = index.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        if (target == currentPage) return
        currentPage = target
        platformSetInt(keyPage, target)
        scope.launch { ReadStore.recordProgress(gid, target, totalPages) }
        recordHistory(target)
        loader.onPageChanged(target)
        if (animatedHint) showMessage("第 ${target + 1} 页")
        scheduleChromeHide()
    }

    /** 按当前模式的步长翻页（点按左右 1/3 或音量键）。 */
    fun pageBy(delta: Int, landscape: Boolean) {
        val step = ReaderEngine.step(pagedDual(landscape))
        goTo(currentPage + delta * step)
    }

    fun next(landscape: Boolean) = pageBy(1, landscape)

    fun previous(landscape: Boolean) = pageBy(-1, landscape)

    /** 手势翻页后由 Pager 回报（不做 clamp 写回，避免和 Pager 打架）。 */
    fun onSweepedTo(index: Int) {
        val target = index.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        if (target == currentPage) return
        currentPage = target
        platformSetInt(keyPage, target)
        scope.launch { ReadStore.recordProgress(gid, target, totalPages) }
        recordHistory(target)
        loader.onPageChanged(target)
        scheduleChromeHide()
    }

    /** 连续模式下由列表回报可见页。 */
    fun onVisiblePage(index: Int) = onSweepedTo(index)

    // ------------------------------------------------------------------ 模式

    /** 按 AUTO 与横竖屏解析出真正的分页模式。 */
    fun pagedMode(landscape: Boolean): ReaderMode =
        if (mode == ReaderMode.AUTO) {
            if (landscape) ReaderMode.DUAL else ReaderMode.LTR
        } else {
            mode
        }

    fun pagedDual(landscape: Boolean): Boolean =
        ReaderEngine.dualActive(
            modeDual = pagedMode(landscape) == ReaderMode.DUAL,
            horizontalMode = pagedMode(landscape) != ReaderMode.VERTICAL,
            landscape = landscape,
        )

    fun cycleMode() {
        mode = mode.next()
        platformSetInt(keyMode, mode.ordinal)
        showTransient("阅读模式：${mode.label}")
        scheduleChromeHide()
    }

    fun applyMode(value: ReaderMode) {
        if (mode == value) return
        mode = value
        platformSetInt(keyMode, mode.ordinal)
        scheduleChromeHide()
    }

    // ------------------------------------------------------------------ 顶底栏

    fun toggleChrome() {
        chromeVisible = !chromeVisible
        interaction++
        if (chromeVisible) scheduleChromeHide() else chromeJob?.cancel()
    }

    fun showChrome() {
        if (!chromeVisible) chromeVisible = true
        interaction++
        scheduleChromeHide()
    }

    /** 用户开始操作时推迟自动隐藏。 */
    fun keepChrome() {
        interaction++
        if (chromeVisible) scheduleChromeHide()
    }

    private fun scheduleChromeHide() {
        chromeJob?.cancel()
        val seconds = AppSettings.readerAutoHideSeconds.coerceIn(0, 10)
        if (seconds == 0 || !chromeVisible) return
        val ticket = interaction
        chromeJob = scope.launch {
            delay(seconds * 1000L)
            if (interaction == ticket) chromeVisible = false
        }
    }

    // ------------------------------------------------------------------ 弹层

    fun openSheet(target: ReaderSheet) {
        sheet = target
        if (target != ReaderSheet.None) keepChrome()
    }

    fun closeSheet() {
        sheet = ReaderSheet.None
        scheduleChromeHide()
    }


    // ------------------------------------------------------------------ 动作

    fun save(index: Int = currentPage) {
        keepChrome()
        scope.launch {
            showMessage("正在保存…")
            showMessage(loader.save(index))
        }
    }

    /** 把当前页图片交给系统分享（优先用已经缓存好的原图，不重复下载）。 */
    fun share(index: Int = currentPage) {
        keepChrome()
        scope.launch {
            showMessage("正在准备图片…")
            showMessage(loader.share(index))
        }
    }

    fun retry(index: Int = currentPage) {
        loader.retry(index)
        showMessage("正在重试…")
    }

    fun showMessage(text: String) {
        messages.trySend(text)
    }

    /** 立即覆盖上一条的状态提示（连点不会排队）。 */
    fun showTransient(text: String) {
        transientHint = text
    }

    /** UI 显示完当前提示后清空；被新的提示顶替时不要清。 */
    fun clearTransient(text: String) {
        if (transientHint == text) transientHint = null
    }

    // ------------------------------------------------------------------ 设置快捷

    fun cycleFit() {
        val next = (AppSettings.readerFit + 1) % ReaderFit.entries.size
        AppSettings.readerFit = next
        showTransient("图片适应：${ReaderFit.fromOrdinal(next).label}")
        keepChrome()
    }
}
