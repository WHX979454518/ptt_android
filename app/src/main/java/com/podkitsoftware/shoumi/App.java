package com.podkitsoftware.shoumi;

import android.app.Application;

import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.picasso.OkHttpDownloader;
import com.squareup.picasso.Picasso;


public class App extends Application {

    private static App instance;

    @Override
    public void onCreate() {
        instance = this;

        super.onCreate();

        Picasso.setSingletonInstance(
                new Picasso.Builder(this)
                        .downloader(new OkHttpDownloader(providesOkHttpClient()))
                        .build());
    }

    public OkHttpClient providesOkHttpClient() {
        final OkHttpClient client = new OkHttpClient();
        client.setCache(new Cache(getCacheDir(), Constants.MAX_CACHE_SIZE));
        return client;
    }

    public static App getInstance() {
        return instance;
    }
}
