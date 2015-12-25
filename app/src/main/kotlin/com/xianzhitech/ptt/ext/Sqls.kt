package com.xianzhitech.ptt.ext


fun <T> Iterable<T>.toSqlSet() = joinToString(separator = ",", prefix = "(", postfix = ")", transform = { "'$it'" })
fun <T> Array<T>.toSqlSet() = joinToString(separator = ",", prefix = "(", postfix = ")", transform = { "'$it'" })
fun <T> LongArray.toSqlSet() = joinToString(separator = ",", prefix = "(", postfix = ")", transform = { "'$it'" })