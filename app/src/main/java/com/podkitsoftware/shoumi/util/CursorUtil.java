package com.podkitsoftware.shoumi.util;

import android.database.Cursor;

public class CursorUtil {
    public static Long getOptionalLong(final Cursor cursor, final String colName) {
        final int columnIndex = cursor.getColumnIndex(colName);
        return columnIndex >= 0 ? cursor.getLong(columnIndex) : null;
    }

    public static long getLong(final Cursor cursor, final String colName) {
        final int columnIndex = cursor.getColumnIndex(colName);
        if (columnIndex >= 0) {
            return cursor.getLong(columnIndex);
        }

        throw new RuntimeException(colName + " not exists in cursor: " + cursor);
    }

    public static String getString(final Cursor cursor, final String colName) {
        final int columnIndex = cursor.getColumnIndex(colName);
        return columnIndex >= 0 ? cursor.getString(columnIndex) : null;
    }
}
