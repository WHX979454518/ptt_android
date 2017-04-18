package com.zrt.ptt.app.console;

import android.support.multidex.MultiDexApplication;

import com.baidu.mapapi.SDKInitializer;

public class App extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();

        SDKInitializer.initialize(this);
    }
}
