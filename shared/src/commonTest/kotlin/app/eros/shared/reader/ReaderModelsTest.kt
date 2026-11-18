// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderModelsTest {

    @Test
    fun `翻页模式按钮按顺序循环`() {
        assertEquals(ReaderMode.RTL, ReaderMode.LTR.next())
        assertEquals(ReaderMode.DUAL, ReaderMode.RTL.next())
        assertEquals(ReaderMode.VERTICAL, ReaderMode.DUAL.next())
        assertEquals(ReaderMode.LTR, ReaderMode.VERTICAL.next())
    }

    @Test
    fun `从持久化序号恢复 未知值退回左到右`() {
        assertEquals(ReaderMode.DUAL, ReaderMode.fromOrdinal(2))
        // 旧版本序号 4 是已删除的「自动」档：退回左→右，不能崩。
        assertEquals(ReaderMode.LTR, ReaderMode.fromOrdinal(4))
        assertEquals(ReaderMode.LTR, ReaderMode.fromOrdinal(99))
    }
}
