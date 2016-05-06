package com.xianzhitech.ptt.ext

import android.util.Log

fun Any.logd(format: String, vararg args: Any?) {
    logtagd(javaClass.simpleName, format, *args)
}

fun Any.logtagd(tag: String, format: String, vararg args: Any?) {
    Log.d(tag, format.format(*args))
}