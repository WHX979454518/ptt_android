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

    fun getColumnIndexOrThrow(columnName: String): Int
    fun getColumnIndex(columnName: String): Int

    fun getString(columnIndex: Int): String
    fun getShort(columnIndex: Int): Short
    fun getInt(columnIndex: Int): Int
    fun getLong(columnIndex: Int): Long
    fun getFloat(columnIndex: Int): Float
    fun getDouble(columnIndex: Int): Double
}