package com.podkitsoftware.shoumi;

import com.raizlabs.android.dbflow.annotation.Database;

/**
 * Created by fanchao on 4/12/15.
 */
@Database(name = AppDatabase.NAME, version = AppDatabase.VERSION)
public class AppDatabase {
    public static final String NAME = "app";
    public static final int VERSION = 1;
}
