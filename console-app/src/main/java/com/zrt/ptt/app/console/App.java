package com.zrt.ptt.app.console;

import com.baidu.mapapi.SDKInitializer;
import com.readystatesoftware.chuck.ChuckInterceptor;
import com.xianzhitech.ptt.BaseApp;

import org.jetbrains.annotations.NotNull;

import okhttp3.OkHttpClient;

public class App extends BaseApp {

    @Override
    public void onCreate() {
        super.onCreate();

        SDKInitializer.initialize(this);
    }

    @NotNull
    @Override
    protected OkHttpClient.Builder onBuildHttpClient() {
        return super.onBuildHttpClient().addInterceptor(new ChuckInterceptor(this));
    }

    @NotNull
    @Override
    public String getCurrentVersion() {
        return BuildConfig.DEBUG ? "dev" : String.valueOf(BuildConfig.VERSION_CODE);
    }
}
