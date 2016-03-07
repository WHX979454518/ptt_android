package com.xianzhitech.ptt.util

import kotlin.reflect.KProperty


class ThreadLocal<T>(private val initFunc : () -> T) : java.lang.ThreadLocal<T>() {
    override fun initialValue() = initFunc()

    operator fun getValue(thisRef: Any?, property: KProperty<*>) : T = get()
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        set(value)
    }
}

fun <T> threadLocal(init : () -> T)  = ThreadLocal(init)