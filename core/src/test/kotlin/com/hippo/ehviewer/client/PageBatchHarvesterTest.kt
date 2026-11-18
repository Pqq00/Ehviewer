// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.client

import com.hippo.ehviewer.net.EhCalls
import com.hippo.ehviewer.net.EhSiteErrorKind
import com.hippo.ehviewer.net.EhSiteException
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 抓取器的**自身语义**测试：会话隔离、批次去重、取消、错误归属。
 *
 * 网络由 `batchFetcher` 注入替身，不碰真实站点 —— 这里要验证的是
 * 「谁该发请求、发几次、取消后多久退出」，而不是 HTTP 本身。
 */
class PageBatchHarvesterTest {

    /** 记录落盘调用；顺便验证 totalPages 没有被写成「已抓到条数」。 */
    private class RecordingStorage : PageBatchHarvester.Storage {
        val writes = mutableListOf<Triple<Long, Int, Int>>()

        override fun read(gid: Long): PageBatchHarvester.Stored? = null

        override fun write(gid: Long, totalPages: Int, urls: List<String>) {
            writes += Triple(gid, totalPages, urls.size)
        }
    }

    /** 站点地址尾部是 1-based 页号，`pageIndex()` 会减一；这里必须从 1 开始，别写 0。 */
    private fun urlFor(page: Int): String =
        "https://e-hentai.org/s/abcdef$page/1234-${page + 1}"

    /** 20 张一批，与线上默认一致。 */
    private fun batchUrls(batch: Int): List<String> =
        (batch * 20 until batch * 20 + 20).map { urlFor(it) }

    private fun harvester(
        storage: PageBatchHarvester.Storage = RecordingStorage(),
        calls: AtomicInteger = AtomicInteger(),
    ): PageBatchHarvester = PageBatchHarvester(storage).apply {
        metaFetcher = { _, _, _ ->
            EhEngine.GalleryPaging(pMax = 0, firstLinks = batchUrls(0))
        }
        batchFetcher = { _, _, batch, _ ->
            calls.incrementAndGet()
            batchUrls(batch)
        }
    }

    @Test
    fun `同一 gid 换 token 不复用旧会话`() {
        val storage = RecordingStorage()
        val calls = AtomicInteger()
        val h = harvester(storage, calls)

        val first = h.fetchBatch(gid = 42L, token = "aaa", pageIndex = 3, totalPages = 200)
        assertEquals(20, first.size)
        assertEquals(1, calls.get(), "第一次抓取应当发一次请求")

        // 同 gid、同页、不同 token：旧会话的地址对新 token 无效，必须重新抓。
        val second = h.fetchBatch(gid = 42L, token = "bbb", pageIndex = 3, totalPages = 200)
        assertEquals(20, second.size)
        assertEquals(2, calls.get(), "换 token 后应当重新发请求，而不是复用旧地址")
    }

    @Test
    fun `同 token 命中缓存不再发请求`() {
        val calls = AtomicInteger()
        val h = harvester(calls = calls)

        val first = h.fetchBatch(gid = 7L, token = "tok", pageIndex = 0, totalPages = 100)
        val second = h.fetchBatch(gid = 7L, token = "tok", pageIndex = 5, totalPages = 100)

        assertEquals(1, calls.get(), "同一批已经在内存里，第二次不该再发请求")
        assertEquals(first[0], second[0], "缓存命中应返回同样的页地址")
    }

    @Test
    fun `并发抓同一批只发一次请求`() {
        val calls = AtomicInteger()
        val started = CountDownLatch(1)
        val done = CountDownLatch(4)
        val h = PageBatchHarvester(RecordingStorage()).apply {
            metaFetcher = { _, _, _ -> EhEngine.GalleryPaging(0, batchUrls(0)) }
            batchFetcher = { _, _, batch, _ ->
                calls.incrementAndGet()
                started.countDown()
                // 拖住第一个请求，让其余线程都进到等待分支。
                Thread.sleep(300)
                batchUrls(batch)
            }
        }
        val results = java.util.Collections.synchronizedList(mutableListOf<Map<Int, String>>())
        repeat(4) {
            Thread {
                started.await(2, TimeUnit.SECONDS)
                results += h.fetchBatch(gid = 9L, token = "t", pageIndex = 1, totalPages = 200)
                done.countDown()
            }.apply { isDaemon = true }.start()
        }
        assertTrue(done.await(5, TimeUnit.SECONDS), "4 个线程都应当返回")
        assertEquals(1, calls.get(), "同一批次只能有一个线程真去抓，其余等结果（实际 ${calls.get()} 次）")
        assertEquals(4, results.size)
        assertEquals(20, results.first().size)
    }

    @Test
    fun `落盘时写入的是详情页总页数而不是已抓条数`() {
        val storage = RecordingStorage()
        val h = harvester(storage)
        h.fetchBatch(gid = 11L, token = "t", pageIndex = 0, totalPages = 451)

        assertEquals(1, storage.writes.size)
        val (gid, totalPages, count) = storage.writes.first()
        assertEquals(11L, gid)
        assertEquals(451, totalPages, "totalPages 应当来自详情页，而不是本批 20 条")
        assertEquals(20, count)
    }

    @Test
    fun `取消后不再发起请求`() {
        val calls = AtomicInteger()
        val h = harvester(calls = calls)
        val handle = EhCalls.newHandle()
        EhCalls.cancel(handle)

        val failure = assertFailsWith<InterruptedIOException> {
            h.fetchBatch(gid = 1L, token = "t", pageIndex = 0, totalPages = 10, cancelKey = handle)
        }
        assertEquals("Canceled", failure.message)
        assertEquals(0, calls.get(), "已经取消就不该再发请求")
    }

    @Test
    fun `等待者可以被取消`() {
        val release = CountDownLatch(1)
        val h = PageBatchHarvester(RecordingStorage()).apply {
            metaFetcher = { _, _, _ -> EhEngine.GalleryPaging(0, batchUrls(0)) }
            batchFetcher = { _, _, batch, _ ->
                release.await(3, TimeUnit.SECONDS)
                batchUrls(batch)
            }
        }
        val owner = Thread {
            h.fetchBatch(gid = 3L, token = "t", pageIndex = 0, totalPages = 100)
        }.apply { isDaemon = true }
        owner.start()
        Thread.sleep(200) // 让 owner 先占住批次

        val handle = EhCalls.newHandle()
        val waiterFailure = java.util.concurrent.CompletableFuture<Throwable?>()
        Thread {
            try {
                h.fetchBatch(gid = 3L, token = "t", pageIndex = 0, totalPages = 100, cancelKey = handle)
                waiterFailure.complete(null)
            } catch (t: Throwable) {
                waiterFailure.complete(t)
            }
        }.apply { isDaemon = true }.start()
        Thread.sleep(200)

        val startedAt = System.nanoTime()
        EhCalls.cancel(handle)
        val failure = waiterFailure.get(3, TimeUnit.SECONDS)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        release.countDown()
        owner.join(2000)

        assertTrue(failure is InterruptedIOException, "等待者应当收到取消异常，实际 $failure")
        assertTrue(elapsedMs < 1000, "等待者应当在一个轮询粒度左右退出，实际 ${elapsedMs}ms")
    }

    @Test
    fun `站点语义错误不重试`() {
        val calls = AtomicInteger()
        val h = PageBatchHarvester(RecordingStorage()).apply {
            metaFetcher = { _, _, _ -> EhEngine.GalleryPaging(0, batchUrls(0)) }
            batchFetcher = { _, _, _, _ ->
                calls.incrementAndGet()
                throw EhSiteException(EhSiteErrorKind.BANNED, "被 ban 了")
            }
        }

        assertFailsWith<EhSiteException> {
            h.fetchBatch(gid = 5L, token = "t", pageIndex = 0, totalPages = 40)
        }
        assertEquals(1, calls.get(), "业务错误不是网络抖动，不该重放")
    }
}
