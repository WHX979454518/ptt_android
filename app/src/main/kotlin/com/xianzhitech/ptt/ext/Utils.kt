package com.xianzhitech.ptt.ext

import kotlin.reflect.KProperty

/**
 * Created by fanchao on 16/01/16.
 */

fun <A, B, C> Pair<A, B>.tripleWith(other: C) = Triple(first, second, other)

fun <T, R> T.pairWith(other: R) = Pair(this, other)

class ThreadLocal<T>(private val initFunc: () -> T) : java.lang.ThreadLocal<T>() {
    override fun initialValue() = initFunc()

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = get()
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        set(value)
    }
}

fun <T> threadLocal(init: () -> T) = ThreadLocal(init)