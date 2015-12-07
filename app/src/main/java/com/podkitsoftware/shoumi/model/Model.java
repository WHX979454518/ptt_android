package com.podkitsoftware.shoumi.model;

import android.content.ContentValues;
import android.database.Cursor;

public interface Model {
    void toValues(ContentValues values);
    void readFrom(Cursor cursor);
}
