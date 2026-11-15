// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderEngineTest {

    @Test
    fun `页码越界归一化到首末页`() {
        assertEquals(0, ReaderEngine.clampPage(-5, 10))
        assertEquals(9, ReaderEngine.clampPage(10, 10))
        assertEquals(0, ReaderEngine.clampPage(3, 0))
    }

    @Test
    fun `单页槽数等于页数`() {
        assertEquals(1, ReaderEngine.slotCount(0, dual = false))
        assertEquals(10, ReaderEngine.slotCount(10, dual = false))
    }

    @Test
    fun `双页模式奇数尾页占一个槽`() {
        assertEquals(5, ReaderEngine.slotCount(10, dual = true))
        assertEquals(6, ReaderEngine.slotCount(11, dual = true))
        assertEquals(1, ReaderEngine.slotCount(1, dual = true))
    }

    @Test
    fun `槽位与页号互转`() {
        assertEquals(4, ReaderEngine.pageForSlot(2, dual = true))
        assertEquals(2, ReaderEngine.pageForSlot(2, dual = false))
        assertEquals(2, ReaderEngine.slotForPage(5, 10, dual = true))
        assertEquals(5, ReaderEngine.slotForPage(5, 10, dual = false))
        // 越界页收敛到最后一个槽
        assertEquals(4, ReaderEngine.slotForPage(99, 10, dual = true))
    }

    @Test
    fun `双页槽位末页只返回一页`() {
        assertEquals(listOf(0, 1), ReaderEngine.slotPages(0, 10, dual = true))
        assertEquals(listOf(8, 9), ReaderEngine.slotPages(4, 10, dual = true))
        // 11 页时最后一个槽只有第 10 页（0-based index 10）
        assertEquals(listOf(10), ReaderEngine.slotPages(5, 11, dual = true))
        // 槽位超出总槽数返回空
        assertTrue(ReaderEngine.slotPages(9, 10, dual = true).isEmpty())
    }

    @Test
    fun `单页槽位只返回一页`() {
        assertEquals(listOf(7), ReaderEngine.slotPages(7, 10, dual = false))
        assertTrue(ReaderEngine.slotPages(10, 10, dual = false).isEmpty())
    }

    @Test
    fun `预加载窗口在首尾页被裁剪`() {
        assertEquals(listOf(0, 1, 2), ReaderEngine.resolveWindow(0, 10))
        assertEquals(listOf(7, 8, 9), ReaderEngine.resolveWindow(9, 10))
        assertEquals(listOf(3, 4, 5, 6, 7), ReaderEngine.resolveWindow(5, 10))
    }

    @Test
    fun `预加载目标页不越过末页`() {
        assertEquals(8, ReaderEngine.preloadTarget(6, 2, 10))
        assertNull(ReaderEngine.preloadTarget(9, 3, 10))
        assertNull(ReaderEngine.preloadTarget(0, 2, 1))
    }

    @Test
    fun `图片预取窗口向后优先 再补回退一页`() {
        // 当前页本身不在窗口里（走交互槽）
        assertEquals(listOf(4, 5, 6, 2), ReaderEngine.preloadWindow(3, 10, ahead = 3, behind = 1))
        // 首屏没有回退页
        assertEquals(listOf(1, 2, 3), ReaderEngine.preloadWindow(0, 10, ahead = 3, behind = 1))
        // 末页被裁剪
        assertEquals(listOf(7, 8, 9, 5), ReaderEngine.preloadWindow(6, 10, ahead = 3, behind = 1))
        assertEquals(listOf(8), ReaderEngine.preloadWindow(9, 10, ahead = 3, behind = 1))
        // 关闭预加载：空窗口（只剩当前页由调用方处理）
        assertTrue(ReaderEngine.preloadWindow(5, 10, ahead = 0, behind = 0).isEmpty())
        // 单页画廊
        assertTrue(ReaderEngine.preloadWindow(0, 1, ahead = 3, behind = 1).isEmpty())
    }

    @Test
    fun `跨批预热取该批头几页`() {
        // 20 页一批：第 17 页属于 20..39 批，预热该批前 2 页
        assertEquals(listOf(20, 21), ReaderEngine.batchWarmPages(20, 100, pagesPerBatch = 20, count = 2))
        assertEquals(listOf(0, 1), ReaderEngine.batchWarmPages(17, 100, pagesPerBatch = 20, count = 2))
        assertEquals(listOf(0, 1), ReaderEngine.batchWarmPages(3, 100, pagesPerBatch = 20, count = 2))
        // 末批不足 2 页
        assertEquals(listOf(100), ReaderEngine.batchWarmPages(101, 101, pagesPerBatch = 20, count = 2))
        assertTrue(ReaderEngine.batchWarmPages(0, 0, pagesPerBatch = 20, count = 2).isEmpty())
    }

    @Test
    fun `淘汰只保留当前页附近`() {
        val loaded = listOf(1, 2, 3, 7, 8, 9)
        assertEquals(setOf(1, 2, 3), ReaderEngine.keepNear(2, loaded))
        assertEquals(setOf(7, 8, 9), ReaderEngine.keepNear(8, loaded))
    }

    @Test
    fun `阅读进度与页码文案`() {
        assertEquals(0.1f, ReaderEngine.progress(0, 10), 0.0001f)
        assertEquals(1f, ReaderEngine.progress(9, 10), 0.0001f)
        assertEquals(0f, ReaderEngine.progress(0, 0), 0.0001f)
        assertEquals("1 / 10", ReaderEngine.pageLabel(0, 10))
        assertEquals("10 / 10", ReaderEngine.pageLabel(99, 10))
    }

    @Test
    fun `点击左右三分之一翻页 中间切工具栏`() {
        assertEquals(ReaderTap.Previous, ReaderEngine.tapAction(0.1f, pagingEnabled = true))
        assertEquals(ReaderTap.Next, ReaderEngine.tapAction(0.9f, pagingEnabled = true))
        assertEquals(ReaderTap.ToggleChrome, ReaderEngine.tapAction(0.5f, pagingEnabled = true))
        assertEquals(ReaderTap.ToggleChrome, ReaderEngine.tapAction(0.1f, pagingEnabled = false))
    }

    @Test
    fun `双页判定 显式双页或横屏左右模式`() {
        assertTrue(ReaderEngine.dualActive(modeDual = true, horizontalMode = false, landscape = false))
        assertTrue(ReaderEngine.dualActive(modeDual = false, horizontalMode = true, landscape = true))
        assertTrue(!ReaderEngine.dualActive(modeDual = false, horizontalMode = false, landscape = true))
    }

    @Test
    fun `翻页在边界停住不循环`() {
        assertEquals(0, ReaderEngine.previousPage(0, 10))
        assertEquals(9, ReaderEngine.nextPage(9, 10))
        assertEquals(1, ReaderEngine.nextPage(0, 10))
    }
}
