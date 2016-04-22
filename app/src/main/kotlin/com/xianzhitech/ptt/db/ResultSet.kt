package com.xianzhitech.ptt.db

import java.io.Closeable

/**
 * Created by fanchao on 10/01/16.
 */
interface ResultSet : Closeable {
    fun getCount(): Int
    fun getColumnNames(): Array<String>

    fun moveToFirst(): Boolean
    fun moveToNext(): Boolean

    fun getString(columnName: String): String
    fun getShort(columnName: String): Short
    fun getInt(columnName: String): Int
    fun getLong(columnName: String): Long
    fun getFloat(columnName: String): Float
    fun getDouble(columnName: String): Double
}