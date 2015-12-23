package com.xianzhitech.ptt.ext

import android.database.Cursor
import java.util.*

/**
 * Created by fanchao on 17/12/15.
 */

fun Cursor.getLongValue(columnName: String): Long = getLong(getColumnIndexOrThrow(columnName))

fun Cursor.getIntValue(columnName: String): Int = getInt(getColumnIndexOrThrow(columnName))
fun Cursor.getStringValue(columnName: String): String = getString(getColumnIndexOrThrow(columnName))

fun Cursor.optStringValue(columnName: String): String? = getString(getColumnIndex(columnName))

inline fun <T> Cursor.forEach(func: (Cursor) -> T) {
    while (moveToNext()) {
        func(this)
    }
}

fun Cursor.countAndClose(countIndex: Int = 0) = this.use { moveToNext(); getInt(countIndex) }
fun <T> Cursor.mapAndClose(mapper: (Cursor) -> T): List<T> = this.use { cursor ->
    ArrayList<T>(count).let { list ->
        cursor.forEach { list += mapper(it) }
        list
    }
}

inline fun <T> Cursor.mapToList(mapper: (Cursor) -> T): List<T> = this.use {
    val result = ArrayList<T>(count)
    while (moveToNext()) {
        result += mapper(this)
    }

    result
}