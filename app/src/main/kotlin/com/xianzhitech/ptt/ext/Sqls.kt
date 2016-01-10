package com.xianzhitech.ptt.ext

import android.database.DatabaseUtils
import kotlin.collections.joinToString

fun String.escapeSql() = DatabaseUtils.sqlEscapeString(this)
fun <T> Iterable<T>.toSqlSet(escape: Boolean = true): String {
    return if (escape) {
        joinToString(separator = ",", prefix = "(", postfix = ")", transform = { it.toString().escapeSql() })
    } else {
        joinToString(separator = ",", prefix = "(", postfix = ")")
    }
}

fun <T> Array<T>.toSqlSet() = joinToString(separator = ",", prefix = "(", postfix = ")", transform = { it.toString().escapeSql() })
fun <T> LongArray.toSqlSet() = joinToString(separator = ",", prefix = "(", postfix = ")", transform = { it.toString().escapeSql() })