package com.xianzhitech.ptt.ext

import com.xianzhitech.ptt.db.ResultSet
import java.util.*

/**
 * Created by fanchao on 17/12/15.
 */

fun ResultSet.getLongValue(columnName: String): Long = getLong(getColumnIndexOrThrow(columnName))

fun ResultSet.getIntValue(columnName: String): Int = getInt(getColumnIndexOrThrow(columnName))
fun ResultSet.getStringValue(columnName: String): String = getString(getColumnIndexOrThrow(columnName))

fun ResultSet.optStringValue(columnName: String): String? = getString(getColumnIndex(columnName))

inline fun <T> ResultSet.forEach(func: (ResultSet) -> T) = this.use {
    while (moveToNext()) {
        func(this)
    }
}

fun ResultSet.countAndClose(countIndex: Int = 0) = this.use { moveToNext(); getInt(countIndex) }
fun <T> ResultSet.mapAndClose(mapper: (ResultSet) -> T): List<T> = this.use { cursor ->
    ArrayList<T>(getCount()).let { list ->
        cursor.forEach { list += mapper(it) }
        list
    }
}

inline fun <T> ResultSet.mapToList(mapper: (ResultSet) -> T): List<T> = this.use {
    val result = ArrayList<T>(getCount())
    while (moveToNext()) {
        result += mapper(this)
    }

    result
}