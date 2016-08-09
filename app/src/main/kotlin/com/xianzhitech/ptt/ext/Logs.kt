package com.xianzhitech.ptt.ext

import org.slf4j.Logger


inline fun Logger.d(func : () -> Any?) {
    if (isDebugEnabled) {
        debug(func()?.toString())
    }
}

inline fun Logger.i(func : () -> Any?) {
    if (isInfoEnabled) {
        info(func()?.toString())
    }
}

inline fun Logger.w(func : () -> Any?) {
    if (isWarnEnabled) {
        warn(func()?.toString())
    }
}

inline fun Logger.e(func: () -> Any?) {
    if (isErrorEnabled) {
        error(func()?.toString())
    }
}

inline fun Logger.e(e : Throwable?, func: () -> Any?) {
    if (isErrorEnabled) {
        error(func()?.toString(), e)
    }
}

fun Array<*>.print() : String {
    return "[${this.joinToString(separator = ",", prefix = "'", postfix = "'")}]"
}