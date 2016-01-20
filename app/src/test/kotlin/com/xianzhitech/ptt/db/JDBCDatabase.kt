package com.xianzhitech.ptt.db

import com.xianzhitech.ptt.ext.toSqlSet
import com.xianzhitech.ptt.ext.transform
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement

/**
 * Created by fanchao on 10/01/16.
 */
class JDBCDatabase(url: String) : Database {
    private val connection: Connection

    init {
        connection = DriverManager.getConnection(url)
    }

    override fun <R> executeInTransaction(func: () -> R): R {
        val oldAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            val result = func()
            connection.commit()
            return result
        } finally {
            connection.autoCommit = oldAutoCommit
        }
    }

    override fun insert(table: String, values: Map<String, Any?>, replaceIfConflicts: Boolean): Int {
        val insertStm = if (replaceIfConflicts) "OR REPLACE" else "OR FAIL"
        return connection.prepareStatement("INSERT $insertStm INTO $table ${values.keys.toSqlSet(false)} VALUES ${values.values.toSqlSlots()}")
                .bind(values.values)
                .executeUpdate()
    }

    override fun query(sql: String, vararg args: Any?): ResultSet {
        return JDBCResultSet(connection.prepareStatement(sql).bind(args.toList()).executeQuery())
    }

    override fun update(table: String, values: Map<String, Any?>, whereClause: String, vararg whereArgs: Any?): Int {
        return connection.prepareStatement("UPDATE $table SET ${values.toSqlUpdateStrings()} ${whereClause.toSqlWhereClause()}")
                .bind(values.values.toList() + whereArgs.toList().transform { it?.toString() })
                .executeUpdate()
    }

    override fun execute(sql: String, vararg args: Any?) {
        connection.prepareStatement(sql).bind(args.toList()).execute()
    }

    override fun delete(table: String, whereClause: String, vararg whereArgs: Any?): Int {
        return connection.prepareStatement("DELETE FROM $table ${whereClause.toSqlWhereClause()}").bind(whereArgs.toList())
                .executeUpdate()
    }

    override fun close() = connection.close()

    private fun <T> Iterable<T>.toSqlSlots() = joinToString(separator = ",", prefix = "(", postfix = ")", transform = { "?" })
    private fun <T> PreparedStatement.bind(values: Iterable<T>): PreparedStatement {
        values.forEachIndexed { i, value -> setString(i + 1, value?.toString()) }
        return this
    }

    private fun String.toSqlWhereClause() = if (this.isNullOrEmpty()) "" else "WHERE $this"

    private fun Map<String, Any?>.toSqlUpdateStrings() =
            entries.joinToString(separator = ",", transform = { "${it.key} = ?" })

    private class JDBCResultSet(val resultSet: java.sql.ResultSet) : ResultSet {
        private val names = Array<String>(resultSet.metaData.columnCount, { resultSet.metaData.getColumnName(it + 1) })

        override fun getCount() = resultSet.fetchSize

        override fun getColumnNames() = names

        override fun moveToFirst() = if (resultSet.isBeforeFirst) moveToNext() else false
        override fun moveToNext() = resultSet.next()
        override fun getColumnIndexOrThrow(columnName: String) = names.indexOf(columnName).apply {
            if (this < 0) throw IllegalArgumentException()
        }

        override fun getColumnIndex(columnName: String) = names.indexOf(columnName)

        override fun getString(columnIndex: Int) = resultSet.getString(columnIndex + 1)
        override fun getShort(columnIndex: Int) = resultSet.getShort(columnIndex + 1)
        override fun getInt(columnIndex: Int) = resultSet.getInt(columnIndex + 1)
        override fun getLong(columnIndex: Int) = resultSet.getLong(columnIndex + 1)
        override fun getFloat(columnIndex: Int) = resultSet.getFloat(columnIndex + 1)
        override fun getDouble(columnIndex: Int) = resultSet.getDouble(columnIndex + 1)
        override fun close() = resultSet.close()
    }

}