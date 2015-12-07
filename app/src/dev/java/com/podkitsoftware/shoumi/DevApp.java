package com.podkitsoftware.shoumi;

import com.facebook.stetho.Stetho;

/**
 * Created by fanchao on 7/12/15.
 */
public class DevApp extends App {

    @Override
    public void onCreate() {
        super.onCreate();

        Stetho.initializeWithDefaults(this);
    }
}
