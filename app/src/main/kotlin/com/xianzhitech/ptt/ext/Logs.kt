package com.xianzhitech.ptt.ext

import android.util.Log
import kotlin.text.format

/**
 * Created by fanchao on 18/12/15.
 */

fun Any.logd(format: String, vararg args: String) {
    Log.d(javaClass.simpleName, format.format(*args))
}