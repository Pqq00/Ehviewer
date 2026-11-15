// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadRetryTest {

    @Test
    fun `重置失败页只影响 FAILED 页`() {
        val manifest = DownloadManifest.create(1, "t", "x", 4).let { base ->
            base.copy(
                pages = listOf(
                    DownloadPage(0, PageStatus.DONE, "001.jpg", 100),
                    DownloadPage(1, PageStatus.FAILED, attempts = 3, error = "timeout"),
                    DownloadPage(2),
                    DownloadPage(3, PageStatus.FAILED, attempts = 2, error = "509"),
                ),
            )
        }
        val reset = resetFailedPages(manifest)
        assertEquals(PageStatus.DONE, reset.pages[0].status)
        assertEquals(PageStatus.PENDING, reset.pages[1].status)
        assertEquals(0, reset.pages[1].attempts)
        assertEquals(null, reset.pages[1].error)
        assertEquals(PageStatus.PENDING, reset.pages[2].status)
        assertEquals(PageStatus.PENDING, reset.pages[3].status)
        assertEquals(0, reset.failedPages)
        assertTrue(reset.donePages == 1)
    }
}
