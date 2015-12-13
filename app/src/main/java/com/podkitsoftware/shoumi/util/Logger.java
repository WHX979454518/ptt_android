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
}
