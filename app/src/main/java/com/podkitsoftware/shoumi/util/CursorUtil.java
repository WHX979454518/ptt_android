package com.podkitsoftware.shoumi.util;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

import rx.functions.Func1;

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

    public static int countAndClose(final Cursor cursor, final int index) {
        try {
            cursor.moveToNext();
            return cursor.getInt(index);
        } finally {
            cursor.close();
        }
    }

    public static <T> List<T> mapCursorAndClose(final Cursor cursor, final Func1<Cursor, T> mapper) {
        try {
            final ArrayList<T> result = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                result.add(mapper.call(cursor));
            }
            return result;
        } finally {
            cursor.close();
        }
    }
}
