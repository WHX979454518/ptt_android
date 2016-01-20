package com.xianzhitech.ptt.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.CursorWrapper
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Created by fanchao on 10/01/16.
 */
class AndroidDatabase(context: Context, name: String, version: Int) : Database {
    private val dbHelper: SQLiteOpenHelper

    init {
        dbHelper = object : SQLiteOpenHelper(context, name, null, version) {
            override fun onCreate(db: SQLiteDatabase?) {
                // Create tables
                db?.apply {
                    beginTransaction()
                    try {
                        execSQL(com.xianzhitech.ptt.model.User.Companion.CREATE_TABLE_SQL)
                        execSQL(com.xianzhitech.ptt.model.Group.Companion.CREATE_TABLE_SQL)
                        execSQL(com.xianzhitech.ptt.model.GroupMembers.Companion.CREATE_TABLE_SQL)
                        execSQL(com.xianzhitech.ptt.model.Room.Companion.CREATE_TABLE_SQL)
                        execSQL(com.xianzhitech.ptt.model.RoomMembers.Companion.CREATE_TABLE_SQL)
                        execSQL(com.xianzhitech.ptt.model.Contacts.Companion.CREATE_TABLE_SQL)
                        setTransactionSuccessful()
                    } finally {
                        endTransaction()
                    }
                }
            }

            override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
                throw UnsupportedOperationException()
            }
        }
    }

    override fun <R> executeInTransaction(func: () -> R): R {
        dbHelper.writableDatabase!!.let {
            it.beginTransaction()
            try {
                val result = func()
                it.setTransactionSuccessful()
                return result
            } finally {
                it.endTransaction()
            }
        }
    }

    override fun insert(table: String, values: Map<String, Any?>, replaceIfConflicts: Boolean): Int {
        return dbHelper.writableDatabase.insertWithOnConflict(table, null, values.toContentValues(),
                if (replaceIfConflicts) SQLiteDatabase.CONFLICT_REPLACE else SQLiteDatabase.CONFLICT_FAIL).let {
            if (it >= 0) 1
            else 0
        }
    }

    override fun query(sql: String, vararg args: Any?): ResultSet {
        return dbHelper.readableDatabase.rawQuery(sql, args.toStringArray()).toResultSet()
    }

    override fun update(table: String, values: Map<String, Any?>, whereClause: String, vararg whereArgs: Any?): Int {
        return dbHelper.writableDatabase.update(table, values.toContentValues(), whereClause, whereArgs.toStringArray())
    }

    override fun delete(table: String, whereClause: String, vararg whereArgs: Any?): Int {
        return dbHelper.writableDatabase.delete(table, whereClause, whereArgs.toStringArray())
    }

    override fun execute(sql: String, vararg args: Any?) {
        dbHelper.writableDatabase.execSQL(sql, args)
    }

    override fun close() {
        dbHelper.close()
    }

    private fun Map<String, Any?>.toContentValues() = ContentValues(size).apply {
        forEach {
            when (it.value) {
                is Int -> put(it.key, it.value as Int)
                is Short -> put(it.key, it.value as Short)
                is Float -> put(it.key, it.value as Float)
                is Double -> put(it.key, it.value as Double)
                is Long -> put(it.key, it.value as Long)
                else -> put(it.key, it.value.toString())
            }
        }
    }

    private fun Cursor.toResultSet(): ResultSet = CursorResultSet(this)

    private fun <T> Array<T>.toStringArray() = Array(this.size, { this[it]?.toString() })

    private class CursorResultSet(cursor: Cursor) : CursorWrapper(cursor), ResultSet
}