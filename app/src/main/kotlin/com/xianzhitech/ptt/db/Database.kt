package com.xianzhitech.ptt.db

import java.io.Closeable


interface Database : Closeable {
    fun <R> executeInTransaction(func: () -> R): R
    fun insert(table: String, values: Map<String, Any?>, replaceIfConflicts: Boolean = true): Int
    fun query(sql: String, vararg args: Any?): ResultSet
    fun update(table: String, values: Map<String, Any?>, whereClause: String, vararg whereArgs: Any?): Int
    fun execute(sql: String, vararg args: Any?): Unit
    fun delete(table: String, whereClause: String, vararg whereArgs: Any?): Int
}

interface TableDefinition {
    val creationSql : String
}

interface DatabaseFactory {
    fun createDatabase(tables : Array<TableDefinition>, version: Int) : Database
}