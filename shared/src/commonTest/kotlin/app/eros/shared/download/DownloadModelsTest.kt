// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadModelsTest {

    private fun jpegBytes(size: Int = 2048): ByteArray =
        ByteArray(size).also {
            it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte()
        }

    @Test
    fun `manifest 创建时按总页数铺平 PENDING`() {
        val m = DownloadManifest.create(gid = 42, token = "t", title = "x", totalPages = 5)
        assertEquals(5, m.pages.size)
        assertTrue(m.pages.all { it.status == PageStatus.PENDING })
        assertEquals(0, m.donePages)
        assertEquals(0f, m.progress)
    }

    @Test
    fun `完成页数与进度按 DONE 计算`() {
        val m = DownloadManifest.create(1, "t", "x", 4).let { base ->
            base.copy(
                pages = base.pages.mapIndexed { i, p ->
                    if (i < 2) p.copy(status = PageStatus.DONE, bytes = 100) else p
                },
            )
        }
        assertEquals(2, m.donePages)
        assertEquals(0.5f, m.progress)
        assertEquals(200, m.downloadedBytes)
    }

    @Test
    fun `魔数校验识别常见图片格式`() {
        assertTrue(looksLikeImage(jpegBytes()))
        assertTrue(looksLikeImage(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertTrue(looksLikeImage("GIF89a............".toByteArray()))
        assertFalse(looksLikeImage("<html>error page</html>".toByteArray()))
        assertFalse(looksLikeImage(ByteArray(0)))
    }

    @Test
    fun `HTML 错误页能被识别`() {
        assertTrue(looksLikeHtmlError("<html><body>Sad Panda</body></html>".toByteArray()))
        assertTrue(looksLikeHtmlError("This gallery is pining for the fjords".toByteArray()))
        assertFalse(looksLikeHtmlError(jpegBytes()))
    }

    @Test
    fun `扩展名从 URL 推断且默认 jpg`() {
        assertEquals("jpg", extensionOf("https://a/b/001.jpg?x=1"))
        assertEquals("png", extensionOf("https://a/b/002.PNG"))
        assertEquals("webp", extensionOf("https://a/b/003.webp"))
        assertEquals("jpg", extensionOf("https://a/b/noext"))
    }

    @Test
    fun `manifest 的 JSON 往返保持字段`() {
        val m = DownloadManifest.create(7, "tok", "标题", 3, thumb = "https://t/1.jpg").copy(
            status = DownloadStatus.PAUSED,
            pages = listOf(
                DownloadPage(0, PageStatus.DONE, "001.jpg", 1234, 1),
                DownloadPage(1, PageStatus.FAILED, null, 0, 3, "timeout"),
                DownloadPage(2),
            ),
        )
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val text = json.encodeToString(DownloadManifest.serializer(), m)
        val back = json.decodeFromString(DownloadManifest.serializer(), text)
        assertEquals(m, back)
    }
}
