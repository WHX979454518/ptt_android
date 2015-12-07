package com.podkitsoftware.shoumi;

import android.app.Application;


public class App extends Application {

    private static App instance;

    @Override
    public void onCreate() {
        instance = this;

        super.onCreate();
    }

    public static App getInstance() {
        return instance;
    }
}
