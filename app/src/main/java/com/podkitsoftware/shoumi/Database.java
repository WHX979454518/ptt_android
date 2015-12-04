package com.podkitsoftware.shoumi;

import android.net.Uri;

@com.raizlabs.android.dbflow.annotation.Database(name = Database.NAME, version = Database.VERSION)
public class Database {
    public static final String NAME = "app";
    public static final int VERSION = 1;

    public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + App.getInstance().getPackageName());
}
