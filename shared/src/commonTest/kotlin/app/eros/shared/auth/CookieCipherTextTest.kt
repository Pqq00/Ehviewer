// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.auth

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CookieCipherTextTest {

    @Test
    fun encodedPayloadRoundTrips() {
        val iv = ByteArray(12) { it.toByte() }
        val payload = "ipb_member_id\t123".encodeToByteArray()
        val stored = CookieCipherText.encode(iv, payload)
        assertTrue(CookieCipherText.isEncrypted(stored))
        assertContentEquals(iv + payload, CookieCipherText.decode(stored))
    }

    @Test
    fun legacyPlainTextIsNotEncrypted() {
        val legacy = "ipb_member_id\t123\te-hentai.org\t/\t0\t1\t1"
        assertFalse(CookieCipherText.isEncrypted(legacy))
        assertNull(CookieCipherText.decode(legacy))
    }

    @Test
    fun corruptedBase64ReturnsNull() {
        assertNull(CookieCipherText.decode("v1:!!!not-base64!!!"))
    }

    @Test
    fun encryptedPrefixIsRequired() {
        val stored = CookieCipherText.encode(ByteArray(12), ByteArray(4))
        assertTrue(stored.startsWith("v1:"))
    }
}
