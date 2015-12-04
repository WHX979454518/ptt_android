package com.podkitsoftware.shoumi;

import android.app.Application;

import com.raizlabs.android.dbflow.config.FlowManager;

public class App extends Application {

    private static App instance;

    @Override
    public void onCreate() {
        instance = this;

        super.onCreate();

        FlowManager.init(this);
    }

    public static App getInstance() {
        return instance;
    }
}
