package com.xianzhitech.ptt.ext

import android.util.Log

fun Any.logd(format: String, vararg args: String) {
    logtagd(javaClass.simpleName, format, *args)
}

fun Any.logtagd(tag: String, format: String, vararg args: String) {
    Log.d(tag, format.format(*args))
}