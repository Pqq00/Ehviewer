// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.reader

/**
 * 阅读器纯逻辑：页码归一化、双页槽位、预加载窗口、淘汰半径、点击分区、进度。
 *
 * 不依赖 Compose / 平台 API，UI 只负责把结果画出来，便于单测覆盖边界
 * （首页/末页/双页奇数尾页/空画廊/越界跳转）。
 */
object ReaderEngine {

    /** 顺序阅读时前后保留的页数（位图淘汰半径）。 */
    const val KEEP_RADIUS: Int = 2

    /** 点击左/右 1/3 翻页的边界。 */
    const val TAP_EDGE: Float = 0.33f

    fun clampPage(index: Int, totalPages: Int): Int {
        val last = (totalPages - 1).coerceAtLeast(0)
        return index.coerceIn(0, last)
    }

    fun pageCountOrOne(totalPages: Int): Int = totalPages.coerceAtLeast(1)

    /** 单个 pager 槽位对应的页步长：双页 2，单页 1。 */
    fun step(dual: Boolean): Int = if (dual) 2 else 1

    /** pager 总槽数；双页模式最后一页只剩一页也算一个槽。 */
    fun slotCount(totalPages: Int, dual: Boolean): Int {
        val pages = pageCountOrOne(totalPages)
        return if (dual) (pages + 1) / 2 else pages
    }

    /** 槽位第一页的页号。 */
    fun pageForSlot(slot: Int, dual: Boolean): Int = slot.coerceAtLeast(0) * step(dual)

    /** 页号所在槽位；末页越界时收敛到最后一个槽。 */
    fun slotForPage(page: Int, totalPages: Int, dual: Boolean): Int {
        val pages = pageCountOrOne(totalPages)
        val slot = clampPage(page, pages) / step(dual)
        return slot.coerceIn(0, (slotCount(pages, dual) - 1).coerceAtLeast(0))
    }

    /** 一个槽位要画的页：双页时第二页存在才显示。 */
    fun slotPages(slot: Int, totalPages: Int, dual: Boolean): List<Int> {
        val pages = pageCountOrOne(totalPages)
        val first = pageForSlot(slot, dual)
        if (first >= pages) return emptyList()
        if (!dual) return listOf(first)
        return if (first + 1 < pages) listOf(first, first + 1) else listOf(first)
    }

    /** 顺序阅读的预加载窗口（当前页 ± radius），已裁剪到有效范围。 */
    fun resolveWindow(page: Int, totalPages: Int, radius: Int = KEEP_RADIUS): List<Int> {
        val pages = pageCountOrOne(totalPages)
        val start = (clampPage(page, pages) - radius).coerceAtLeast(0)
        val end = (clampPage(page, pages) + radius).coerceAtMost(pages - 1)
        return (start..end).toList()
    }

    /** 提前抓取的批次目标页（当前页 + 预加载页数）；越界返回 null。 */
    fun preloadTarget(page: Int, ahead: Int, totalPages: Int): Int? {
        val pages = pageCountOrOne(totalPages)
        val target = clampPage(page, pages) + ahead.coerceAtLeast(0)
        return if (target < pages) target else null
    }

    /**
     * 顺序阅读的**图片**预取窗口：当前页之后 [ahead] 页在前，回退 [behind] 页在后。
     *
     * 与 [resolveWindow] 的区别：那个是"渲染窗口"（对称 ±radius，双页/相邻槽位用），
     * 这个是"下载队列"（有方向、按优先级排序，不包含当前页本身 —— 当前页走交互槽）。
     */
    fun preloadWindow(
        page: Int,
        totalPages: Int,
        ahead: Int,
        behind: Int = 1,
    ): List<Int> {
        val pages = pageCountOrOne(totalPages)
        if (pages <= 0) return emptyList()
        val center = clampPage(page, pages)
        val out = ArrayList<Int>(ahead.coerceAtLeast(0) + behind.coerceAtLeast(0))
        for (i in 1..ahead.coerceAtLeast(0)) {
            val p = center + i
            if (p < pages) out += p
        }
        for (i in 1..behind.coerceAtLeast(0)) {
            val p = center - i
            if (p >= 0) out += p
        }
        return out
    }

    /** 某个批次值得顺手预热的头几页（跨批时用）。 */
    fun batchWarmPages(page: Int, totalPages: Int, pagesPerBatch: Int, count: Int): List<Int> {
        if (totalPages <= 0 || pagesPerBatch <= 0 || count <= 0) return emptyList()
        val pages = totalPages
        val start = clampPage(page, pages) / pagesPerBatch * pagesPerBatch
        val end = (start + count).coerceAtMost(pages)
        return (start until end).toList()
    }

    /** 淘汰时保留哪些已加载页。 */
    fun keepNear(page: Int, loadedPages: Collection<Int>, radius: Int = KEEP_RADIUS): Set<Int> {
        val center = page
        return loadedPages.filterTo(mutableSetOf()) { kotlin.math.abs(it - center) <= radius }
    }

    /** 阅读进度 0..1；总页数非法时为 0。 */
    fun progress(page: Int, totalPages: Int): Float {
        val pages = pageCountOrOne(totalPages)
        if (totalPages <= 0) return 0f
        return (clampPage(page, pages) + 1).toFloat() / pages
    }

    /** 顶栏/底栏页码文案。 */
    fun pageLabel(page: Int, totalPages: Int): String {
        val pages = pageCountOrOne(totalPages)
        return "${clampPage(page, pages) + 1} / $pages"
    }

    fun nextPage(page: Int, totalPages: Int): Int = clampPage(page + 1, totalPages)

    fun previousPage(page: Int, totalPages: Int): Int = clampPage(page - 1, totalPages)

    /** 点击分区：左右 1/3 翻页，中间切换工具栏；关闭点击翻页时一律切工具栏。 */
    fun tapAction(fraction: Float, pagingEnabled: Boolean): ReaderTap {
        if (!pagingEnabled) return ReaderTap.ToggleChrome
        return when {
            fraction < TAP_EDGE -> ReaderTap.Previous
            fraction > 1f - TAP_EDGE -> ReaderTap.Next
            else -> ReaderTap.ToggleChrome
        }
    }

    /** 双页判定：显式双页模式，或左右模式下的横屏。 */
    fun dualActive(modeDual: Boolean, horizontalMode: Boolean, landscape: Boolean): Boolean =
        modeDual || (horizontalMode && landscape)
}

enum class ReaderTap { Previous, Next, ToggleChrome }
