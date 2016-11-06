package com.xianzhitech.ptt.ext

import android.os.SystemClock
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

inline fun Logger.trace(func: () -> Any?) {
    if (isTraceEnabled) {
        trace(func()?.toString())
    }
}

inline fun <R> Logger.perf(msg : CharSequence, func: () -> R) : R {
    var startTime = 0L
    if (isTraceEnabled) {
        trace("{} begins", msg)
        startTime = SystemClock.uptimeMillis()
    }

    try {
        return func()
    }
    finally {
        if (isTraceEnabled) {
            trace("{} end: {} ms", msg, SystemClock.uptimeMillis() - startTime)
        }
    }
}

fun Array<*>.print() : String {
    return "[${this.joinToString(separator = ",", prefix = "'", postfix = "'")}]"
}