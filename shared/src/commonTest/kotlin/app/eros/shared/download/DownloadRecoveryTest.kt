// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadRecoveryTest {

    private fun manifest(
        total: Int = 3,
        status: DownloadStatus = DownloadStatus.RUNNING,
        pages: List<DownloadPage> = emptyList(),
    ): DownloadManifest = DownloadManifest.create(1, "t", "x", total).copy(status = status, pages = pages)

    @Test
    fun `DONE 页文件缺失时回退 PENDING 并重新排队`() {
        val pages = listOf(
            DownloadPage(0, PageStatus.DONE, "001.jpg", 2048),
            DownloadPage(1, PageStatus.DONE, "002.jpg", 2048),
            DownloadPage(2, PageStatus.DONE, "003.jpg", 2048),
        )
        val sizes = mapOf(0 to 2048L, 1 to 0L, 2 to 2048L)
        val fixed = manifest(pages = pages, status = DownloadStatus.DONE).reconciled { sizes.getValue(it) }

        assertEquals(PageStatus.PENDING, fixed.pages[1].status)
        assertEquals(0L, fixed.pages[1].bytes)
        assertEquals(2, fixed.donePages)
        assertEquals(DownloadStatus.QUEUED, fixed.status)
    }

    @Test
    fun `文件小于下限也视为损坏`() {
        val pages = listOf(DownloadPage(0, PageStatus.DONE, "001.jpg", 2048))
        val fixed = manifest(total = 1, status = DownloadStatus.DONE, pages = pages).reconciled { 100L }

        assertEquals(PageStatus.PENDING, fixed.pages[0].status)
        assertTrue(fixed.pages[0].error?.contains("过小") == true)
    }

    @Test
    fun `上次进程被杀留下的中间态恢复为 QUEUED`() {
        listOf(
            DownloadStatus.RUNNING,
            DownloadStatus.VERIFYING,
            DownloadStatus.COMMITTING,
            DownloadStatus.RETRY_WAIT,
        ).forEach { status ->
            val fixed = manifest(total = 2, status = status).reconciled { 0L }
            assertEquals(DownloadStatus.QUEUED, fixed.status, "$status 应恢复为 QUEUED")
        }
    }

    @Test
    fun `页数非法或索引越界时清单被归一化`() {
        val pages = listOf(
            DownloadPage(0, PageStatus.DONE, "001.jpg", 2048),
            DownloadPage(9, PageStatus.DONE, "010.jpg", 2048),
            DownloadPage(0, PageStatus.FAILED, null, 0, 3, "timeout"),
        )
        val fixed = manifest(total = 2, pages = pages).reconciled { 2048L }

        assertEquals(2, fixed.pages.size)
        assertEquals(listOf(0, 1), fixed.pages.map { it.index })
        assertEquals(PageStatus.DONE, fixed.pages[0].status)
        assertEquals(PageStatus.PENDING, fixed.pages[1].status)
    }

    @Test
    fun `页数为零的损坏清单直接标记失败`() {
        val fixed = manifest(total = 0).reconciled { 0L }
        assertEquals(DownloadStatus.FAILED, fixed.status)
        assertTrue(fixed.error?.contains("页数非法") == true)
    }

    @Test
    fun `暂停状态不会被恢复逻辑改写`() {
        val fixed = manifest(total = 1, status = DownloadStatus.PAUSED).reconciled { 0L }
        assertEquals(DownloadStatus.PAUSED, fixed.status)
    }

    @Test
    fun `状态集合工具判断正确`() {
        assertTrue(DownloadStatus.VERIFYING.isBusy)
        assertTrue(DownloadStatus.RETRY_WAIT.isBusy)
        assertTrue(DownloadStatus.PAUSED.needsForeground)
        assertEquals(false, DownloadStatus.DONE.isBusy)
        assertEquals(false, DownloadStatus.FAILED.needsForeground)
    }
}
