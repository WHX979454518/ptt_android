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

fun Cursor.countAndClose(index: Int): Int = this.use { moveToNext(); getInt(index) }

inline fun <T> Cursor.mapToList(mapper: (Cursor) -> T): List<T> = this.use {
    val result = ArrayList<T>(count)
    while (moveToNext()) {
        result += mapper(this)
    }

    result
}