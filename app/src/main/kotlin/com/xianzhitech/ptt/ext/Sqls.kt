package com.xianzhitech.ptt.ext

import kotlin.collections.joinToString


fun <T> Iterable<T>.toSqlSet() = joinToString(separator = ",", prefix = "(", postfix = ")", transform = { "'$it'" })
fun <T> Array<T>.toSqlSet() = joinToString(separator = ",", prefix = "(", postfix = ")", transform = { "'$it'" })
fun <T> LongArray.toSqlSet() = joinToString(separator = ",", prefix = "(", postfix = ")", transform = { "'$it'" })