package com.podkitsoftware.shoumi.util;

import android.util.Log;

import java.util.Locale;

/**
 * Created by fanchao on 13/12/15.
 */
public class Logger {

    public static void d(final String tag, final String format, final Object...args) {
        Log.d(tag, String.format(Locale.ENGLISH, format, args));
    }

    public static void w(final String tag, final String format, final Object... args) {
        Log.w(tag, String.format(Locale.ENGLISH, format, args));
    }

    public static void e(final String tag, final String format, final Object... args) {
        Log.e(tag, String.format(Locale.ENGLISH, format, args));
    }
}
