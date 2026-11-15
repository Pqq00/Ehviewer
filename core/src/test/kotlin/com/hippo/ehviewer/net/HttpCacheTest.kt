// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.hippo.ehviewer.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpCacheTest {

    @Test
    fun `policy picks page list for gallery paging urls`() {
        assertEquals(
            HttpCachePolicy.PAGE_LIST,
            cachePolicyFor("https://e-hentai.org/g/123/abc/?p=2", false),
        )
        assertEquals(
            HttpCachePolicy.DETAIL,
            cachePolicyFor("https://e-hentai.org/g/123/abc/", false),
        )
        assertEquals(
            HttpCachePolicy.NONE,
            cachePolicyFor("https://e-hentai.org/g/123/abc/", true),
        )
        assertEquals(
            HttpCachePolicy.NONE,
            cachePolicyFor("https://e-hentai.org/api.php", false),
        )
    }

    @Test
    fun `memory bridge returns fresh entry and drops expired one`() {
        val bridge = MemoryHttpCacheBridge()
        val now = 1_000L
        bridge.put(
            "https://e-hentai.org/",
            CachedHttpResponse("hello".encodeToByteArray(), "text/html", now + 5_000),
        )

        val hit = bridge.get("https://e-hentai.org/", now + 1_000)
        assertNotNull(hit)
        assertEquals("hello", hit.body.decodeToString())

        assertNull(bridge.get("https://e-hentai.org/", now + 6_000))
    }

    @Test
    fun `memory bridge removes by url prefix`() {
        val bridge = MemoryHttpCacheBridge()
        val expiry = System.currentTimeMillis() + 60_000
        bridge.put("https://e-hentai.org/g/1/a/?p=0", CachedHttpResponse(byteArrayOf(1), null, expiry))
        bridge.put("https://e-hentai.org/g/1/a/?p=1", CachedHttpResponse(byteArrayOf(2), null, expiry))
        bridge.put("https://e-hentai.org/g/2/b/", CachedHttpResponse(byteArrayOf(3), null, expiry))

        bridge.removePrefix("https://e-hentai.org/g/1/a/")

        assertNull(bridge.get("https://e-hentai.org/g/1/a/?p=0", System.currentTimeMillis()))
        assertNull(bridge.get("https://e-hentai.org/g/1/a/?p=1", System.currentTimeMillis()))
        assertTrue(bridge.get("https://e-hentai.org/g/2/b/", System.currentTimeMillis()) != null)
    }
}
