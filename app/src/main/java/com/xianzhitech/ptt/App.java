package com.xianzhitech.ptt;

import android.app.Application;

import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.picasso.OkHttpDownloader;
import com.squareup.picasso.Picasso;
import com.xianzhitech.ptt.engine.TalkEngineProvider;
import com.xianzhitech.ptt.engine.WebRtcTalkEngine;
import com.xianzhitech.ptt.service.provider.AuthProvider;
import com.xianzhitech.ptt.service.provider.SignalProvider;
import com.xianzhitech.ptt.service.provider.SocketIOProvider;
import com.xianzhitech.ptt.util.Lazy;

import java.util.concurrent.Executors;

import rx.Scheduler;
import rx.schedulers.Schedulers;


public class App extends Application implements AppComponent {

    private final Lazy<SocketIOProvider> signalProvider = new Lazy<>(() -> new SocketIOProvider(providesBroker(), "http://106.186.124.143:3000/"));
    private final Lazy<TalkEngineProvider> talkEngineFactory = new Lazy<>(() -> () -> new WebRtcTalkEngine(this));
    private final Lazy<OkHttpClient> okHttpClient = new Lazy<>(() -> {
        final OkHttpClient client = new OkHttpClient();
        client.setCache(new Cache(getCacheDir(), Constants.MAX_CACHE_SIZE));
        return client;
    });

    private final Lazy<Database> database = new Lazy<>(() -> new Database(App.this, Constants.DB_NAME, Constants.DB_VERSION));
    private final Lazy<Broker> broker = new Lazy<>(() -> new Broker(providesDatabase()));
    private final Lazy<AuthProvider> authProvider = new Lazy<>(signalProvider::get);
    private final Lazy<Scheduler> talkServiceScheduler = new Lazy<>(() -> Schedulers.from(Executors.newSingleThreadExecutor()));

    @Override
    public void onCreate() {
        super.onCreate();

        Picasso.setSingletonInstance(
                new Picasso.Builder(this)
                        .downloader(new OkHttpDownloader(providesHttpClient()))
                        .build());
    }

    @Override
    public OkHttpClient providesHttpClient() {
        return okHttpClient.get();
    }

    @Override
    public SignalProvider providesSignal() {
        return signalProvider.get();
    }

    @Override
    public TalkEngineProvider providesTalkEngine() {
        return talkEngineFactory.get();
    }

    @Override
    public Database providesDatabase() {
        return database.get();
    }

    @Override
    public Broker providesBroker() {
        return broker.get();
    }

    @Override
    public AuthProvider providesAuth() {
        return authProvider.get();
    }
}
