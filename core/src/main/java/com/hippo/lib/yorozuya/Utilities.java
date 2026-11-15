/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.lib.yorozuya;

import androidx.annotation.Nullable;

/**
 * Subset of EhViewer's Utilities: only the array helpers currently needed by the port.
 */
public final class Utilities {

    private Utilities() {
    }

    /**
     * Whether the array contain the element
     *
     * @param array the array
     * @param ch    the element
     * @return true for the array contain the element
     */
    public static boolean contain(@Nullable char[] array, char ch) {
        if (null == array) {
            return false;
        }

        for (char c : array) {
            if (c == ch) {
                return true;
            }
        }
        return false;
    }
}
