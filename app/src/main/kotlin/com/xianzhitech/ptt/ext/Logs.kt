package com.xianzhitech.ptt.ext

import android.util.Log

fun Any.logd(format: String, vararg args: String) {
    Log.d(javaClass.simpleName, format.format(*args))
}