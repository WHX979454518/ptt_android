package com.xianzhitech.ptt.model;

import android.content.ContentValues;
import android.database.Cursor;

public interface Model {
    String getTableName();
    void toValues(ContentValues values);
    void readFrom(Cursor cursor);
}
