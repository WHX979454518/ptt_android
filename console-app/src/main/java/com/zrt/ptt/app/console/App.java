package com.zrt.ptt.app.console;

import com.baidu.mapapi.SDKInitializer;
import com.xianzhitech.ptt.BaseApp;

import org.jetbrains.annotations.NotNull;

public class App extends BaseApp {

    @Override
    public void onCreate() {
        super.onCreate();

        SDKInitializer.initialize(this);
    }

    @NotNull
    @Override
    public String getCurrentVersion() {
        return BuildConfig.DEBUG ? "dev" : String.valueOf(BuildConfig.VERSION_CODE);
    }
}
