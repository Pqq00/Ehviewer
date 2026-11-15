/*
 * Copyright 2026, eros-miuix contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hippo.ehviewer.core;

/**
 * Minimal stand-in for android.util.Log so the ported EhViewer code can run on a plain JVM.
 */
public final class MiuixLog {

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("miuix.log", "false"));

    private MiuixLog() {
    }

    public static int v(String tag, String msg) {
        return print("V", tag, msg, null);
    }

    public static int d(String tag, String msg) {
        return print("D", tag, msg, null);
    }

    public static int i(String tag, String msg) {
        return print("I", tag, msg, null);
    }

    public static int w(String tag, String msg) {
        return print("W", tag, msg, null);
    }

    public static int w(String tag, String msg, Throwable tr) {
        return print("W", tag, msg, tr);
    }

    public static int e(String tag, String msg) {
        return print("E", tag, msg, null);
    }

    public static int e(String tag, String msg, Throwable tr) {
        return print("E", tag, msg, tr);
    }

    private static int print(String level, String tag, String msg, Throwable tr) {
        if (!ENABLED) {
            return 0;
        }
        System.out.println("[" + level + "/" + tag + "] " + msg);
        if (tr != null) {
            tr.printStackTrace(System.out);
        }
        return 1;
    }
}
