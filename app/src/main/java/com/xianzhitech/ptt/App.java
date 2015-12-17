package com.xianzhitech.ptt;

import android.app.Application;

import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.picasso.OkHttpDownloader;
import com.squareup.picasso.Picasso;
import com.xianzhitech.ptt.engine.ITalkEngineFactory;
import com.xianzhitech.ptt.engine.WebRtcTalkEngine;
import com.xianzhitech.ptt.util.Lazy;
import com.xianzhitech.service.provider.AuthProvider;
import com.xianzhitech.service.provider.SignalProvider;
import com.xianzhitech.service.provider.SocketIOProvider;


public class App extends Application implements AppComponent {

    private final Lazy<SocketIOProvider> signalProvider = new Lazy<>(() -> new SocketIOProvider(providesBroker(), "http://106.186.124.143:3000/"));
    private final Lazy<ITalkEngineFactory> talkEngineFactory = new Lazy<>(() -> WebRtcTalkEngine::new);
    private final Lazy<OkHttpClient> okHttpClient = new Lazy<>(() -> {
        final OkHttpClient client = new OkHttpClient();
        client.setCache(new Cache(getCacheDir(), Constants.MAX_CACHE_SIZE));
        return client;
    });

    private final Lazy<Database> database = new Lazy<>(() -> new Database(App.this, Constants.DB_NAME, Constants.DB_VERSION));
    private final Lazy<Broker> broker = new Lazy<>(() -> new Broker(providesDatabase()));
    private final Lazy<AuthProvider> authProvider = new Lazy<>(signalProvider::get);

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
    public ITalkEngineFactory providesTalkEngineFactory() {
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
