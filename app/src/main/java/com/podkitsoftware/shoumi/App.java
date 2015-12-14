package com.podkitsoftware.shoumi;

import android.app.Application;

import com.podkitsoftware.shoumi.engine.TalkEngineFactory;
import com.podkitsoftware.shoumi.engine.WebRtcTalkEngine;
import com.podkitsoftware.shoumi.service.signal.SignalService;
import com.podkitsoftware.shoumi.service.signal.WebSocketSignalService;
import com.podkitsoftware.shoumi.util.Lazy;
import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.picasso.OkHttpDownloader;
import com.squareup.picasso.Picasso;


public class App extends Application implements AppComponent {

    private final Lazy<SignalService> signalService = new Lazy<>(() -> new WebSocketSignalService("", 80));
    private final Lazy<TalkEngineFactory> talkEngineFactory = new Lazy<>(() -> WebRtcTalkEngine::new);
    private final Lazy<OkHttpClient> okHttpClient = new Lazy<>(() -> {
        final OkHttpClient client = new OkHttpClient();
        client.setCache(new Cache(getCacheDir(), Constants.MAX_CACHE_SIZE));
        return client;
    });

    private final Lazy<Database> database = new Lazy<>(() -> new Database(App.this, Constants.DB_NAME, Constants.DB_VERSION));
    private final Lazy<Broker> broker = new Lazy<>(() -> new Broker(providesDatabase()));

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
    public SignalService providesSignalService() {
        return signalService.get();
    }

    @Override
    public TalkEngineFactory providesTalkEngineFactory() {
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
}
