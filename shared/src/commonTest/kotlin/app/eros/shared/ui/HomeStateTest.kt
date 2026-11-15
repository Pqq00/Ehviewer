// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import app.eros.shared.data.cache.GalleryCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HomeStateTest {

    @BeforeTest
    fun clearCache() = runBlocking { GalleryCache.clearAll() }

    private fun awaitLoaded(state: HomeState) = runBlocking {
        withTimeout(5_000) {
            while (state.initialLoading || state.items.isEmpty()) {
                delay(10)
            }
        }
    }

    @Test
    fun `首次加载成功会填充列表并只请求一次`() {
        val api = FakeEhApi(pageSize = 3)
        val state = HomeState(api)
        state.ensureLoaded()
        awaitLoaded(state)

        assertEquals(3, state.items.size)
        assertEquals(1, api.frontPageCalls)
        assertEquals(null, state.error)
    }

    @Test
    fun `加载失败会记录错误信息`() {
        val api = FakeEhApi().apply { failure = RuntimeException("boom") }
        val state = HomeState(api)
        state.ensureLoaded()
        runBlocking {
            withTimeout(5_000) {
                while (state.initialLoading) delay(10)
            }
        }
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("boom"))
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `切换分类会重新请求并带上分类掩码`() {
        val api = FakeEhApi(pageSize = 2)
        val state = HomeState(api)
        state.ensureLoaded()
        awaitLoaded(state)
        val callsAfterFirst = api.frontPageCalls

        state.selectCats(0x2)
        runBlocking {
            withTimeout(5_000) {
                while (state.cats != 0x2 || api.frontPageCalls <= callsAfterFirst) delay(10)
            }
        }

        assertEquals(0x2, state.cats)
        assertEquals(0x2, api.lastCats)
        assertEquals(callsAfterFirst + 1, api.frontPageCalls)
    }
}
