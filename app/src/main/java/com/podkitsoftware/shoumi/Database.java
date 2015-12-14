package com.podkitsoftware.shoumi;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.util.Log;

import com.podkitsoftware.shoumi.model.Group;
import com.podkitsoftware.shoumi.model.GroupMember;
import com.podkitsoftware.shoumi.model.Person;
import com.squareup.sqlbrite.BriteDatabase;
import com.squareup.sqlbrite.QueryObservable;
import com.squareup.sqlbrite.SqlBrite;

import java.io.Closeable;
import java.io.IOException;

public class Database implements Closeable {

    private final BriteDatabase db;
    public final String name;

    public Database(final Context context, final String dbName, final int dbVersion) {
        this.name = dbName;
        db = SqlBrite
                .create(message -> Log.d("Database", message))
                .wrapDatabaseHelper(new SQLiteOpenHelper(context, dbName, null, dbVersion) {
                    @Override
                    public void onCreate(SQLiteDatabase db) {
                        // Create tables
                        db.beginTransaction();
                        try {
                            db.execSQL(Person.getCreateTableSql());
                            db.execSQL(Group.getCreateTableSql());
                            db.execSQL(GroupMember.getCreateTableSql());
                            db.setTransactionSuccessful();
                        } finally {
                            db.endTransaction();
                        }
                    }

                    @Override
                    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

                    }
                });
        db.setLoggingEnabled(BuildConfig.DEBUG);
    }

    @CheckResult
    @NonNull
    public BriteDatabase.Transaction newTransaction() {
        return db.newTransaction();
    }

    public long insert(String table, ContentValues values, int conflictAlgorithm) {
        return db.insert(table, values, conflictAlgorithm);
    }

    @CheckResult
    @NonNull
    public QueryObservable createQuery(String table, String sql, String... args) {
        return db.createQuery(table, sql, args);
    }

    public int delete(String table, String whereClause, String... whereArgs) {
        return db.delete(table, whereClause, whereArgs);
    }

    public int update(String table, ContentValues values, String whereClause, String... whereArgs) {
        return db.update(table, values, whereClause, whereArgs);
    }

    @Override
    public void close() throws IOException {
        db.close();
    }

    public void execute(String sql, Object... args) {
        db.execute(sql, args);
    }

    public long insert(String table, ContentValues values) {
        return db.insert(table, values);
    }

    @CheckResult
    public Cursor query(String sql, String... args) {
        return db.query(sql, args);
    }

    public void executeAndTrigger(String table, String sql, Object... args) {
        db.executeAndTrigger(table, sql, args);
    }

    public int update(String table, ContentValues values, int conflictAlgorithm, String whereClause, String... whereArgs) {
        return db.update(table, values, conflictAlgorithm, whereClause, whereArgs);
    }

    @CheckResult
    @NonNull
    public QueryObservable createQuery(Iterable<String> tables, String sql, String... args) {
        return db.createQuery(tables, sql, args);
    }

    public void execute(String sql) {
        db.execute(sql);
    }

    public void executeAndTrigger(String table, String sql) {
        db.executeAndTrigger(table, sql);
    }
}
