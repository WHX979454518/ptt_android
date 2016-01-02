package com.xianzhitech.ptt.ext

import android.database.DatabaseUtils

fun String.escapeSql() = DatabaseUtils.sqlEscapeString(this)
fun <T> Iterable<T>.toSqlSet() = joinToString(separator = ",", prefix = "(", postfix = ")", transform = { it.toString().escapeSql() })
fun <T> Array<T>.toSqlSet() = joinToString(separator = ",", prefix = "(", postfix = ")", transform = { it.toString().escapeSql() })
fun <T> LongArray.toSqlSet() = joinToString(separator = ",", prefix = "(", postfix = ")", transform = { it.toString().escapeSql() })