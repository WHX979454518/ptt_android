package com.xianzhitech.ptt.ext

import com.xianzhitech.ptt.db.ResultSet
import java.util.*

inline fun <T> ResultSet.forEach(func: (ResultSet) -> T) = this.use {
    while (moveToNext()) {
        func(this)
    }
}

fun ResultSet.countAndClose(countIndex: Int = 0) = this.use { moveToNext(); getInt(getColumnNames()[0]) }
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