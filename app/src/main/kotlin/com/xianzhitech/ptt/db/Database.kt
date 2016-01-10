package com.xianzhitech.ptt.db

import java.io.Closeable


interface Database : Closeable {
    fun <R> executeInTransaction(func: () -> R): R
    fun insert(table: String, values: Map<String, Any?>, replaceIfConflicts: Boolean = false): Int
    fun query(sql: String, vararg args: Any?): ResultSet
    fun update(table: String, values: Map<String, Any?>, whereClause: String, vararg whereArgs: Any?): Int
    fun execute(sql: String, vararg args: Any?): Unit
    fun delete(table: String, whereClause: String, vararg whereArgs: Any?): Int
}



//class ADatabase(context: Context, val name: String, dbVersion: Int) : Closeable {
//
//    private val db: BriteDatabase
//
//    init {
//        db = SqlBrite.create { message -> Log.d("Database", message) }.wrapDatabaseHelper(object : SQLiteOpenHelper(context, name, null, dbVersion) {
//            override fun onCreate(db: SQLiteDatabase) {
//                // Create tables
//                db.beginTransaction()
//                try {
//                    db.execSQL(Person.CREATE_TABLE_SQL)
//                    db.execSQL(Group.CREATE_TABLE_SQL)
//                    db.execSQL(GroupMembers.CREATE_TABLE_SQL)
//                    db.execSQL(Conversation.CREATE_TABLE_SQL)
//                    db.execSQL(ConversationMembers.CREATE_TABLE_SQL)
//                    db.execSQL(Contacts.CREATE_TABLE_SQL)
//                    db.setTransactionSuccessful()
//                } finally {
//                    db.endTransaction()
//                }
//            }
//
//            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
//
//            }
//        })
//        db.setLoggingEnabled(BuildConfig.DEBUG)
//    }
//
//    @CheckResult
//    fun newTransaction(): BriteDatabase.Transaction {
//        return db.newTransaction()
//    }
//
//    fun insert(table: String, values: ContentValues, conflictAlgorithm: Int): Long {
//        return db.insert(table, values, conflictAlgorithm)
//    }
//
//    @CheckResult
//    fun createQuery(table: String, sql: String, vararg args: String): QueryObservable {
//        return db.createQuery(table, sql, *args)
//    }
//
//    fun delete(table: String, whereClause: String, vararg whereArgs: String): Int {
//        return db.delete(table, whereClause, *whereArgs)
//    }
//
//    fun update(table: String, values: ContentValues, whereClause: String, vararg whereArgs: String): Int {
//        return db.update(table, values, whereClause, *whereArgs)
//    }
//
//    @Throws(IOException::class)
//    override fun close() {
//        db.close()
//    }
//
//    fun execute(sql: String, vararg args: Any) {
//        db.execute(sql, *args)
//    }
//
//    fun insert(table: String, values: ContentValues): Long {
//        return db.insert(table, values)
//    }
//
//    @CheckResult
//    fun query(sql: String, vararg args: String): Cursor {
//        return db.query(sql, *args)
//    }
//
//    fun executeAndTrigger(table: String, sql: String, vararg args: Any) {
//        db.executeAndTrigger(table, sql, *args)
//    }
//
//    fun update(table: String, values: ContentValues, conflictAlgorithm: Int, whereClause: String, vararg whereArgs: String): Int {
//        return db.update(table, values, conflictAlgorithm, whereClause, *whereArgs)
//    }
//
//    @CheckResult
//    fun createQuery(tables: Iterable<String>, sql: String, vararg args: String): QueryObservable {
//        return db.createQuery(tables, sql, *args)
//    }
//
//    fun execute(sql: String) {
//        db.execute(sql)
//    }
//
//    fun executeAndTrigger(table: String, sql: String) {
//        db.executeAndTrigger(table, sql)
//    }
//}
