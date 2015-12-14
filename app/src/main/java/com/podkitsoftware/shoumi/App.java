package com.podkitsoftware.shoumi;

import android.app.Application;

import com.podkitsoftware.shoumi.engine.ITalkEngineFactory;
import com.podkitsoftware.shoumi.engine.WebRtcTalkEngine;
import com.podkitsoftware.shoumi.service.auth.IAuthService;
import com.podkitsoftware.shoumi.service.signal.ISignalService;
import com.podkitsoftware.shoumi.service.signal.WebSocketSignalService;
import com.podkitsoftware.shoumi.util.Lazy;
import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.picasso.OkHttpDownloader;
import com.squareup.picasso.Picasso;


public class App extends Application implements AppComponent {

    private final Lazy<WebSocketSignalService> signalService = new Lazy<>(() -> new WebSocketSignalService("", 80));
    private final Lazy<ITalkEngineFactory> talkEngineFactory = new Lazy<>(() -> WebRtcTalkEngine::new);
    private final Lazy<OkHttpClient> okHttpClient = new Lazy<>(() -> {
        final OkHttpClient client = new OkHttpClient();
        client.setCache(new Cache(getCacheDir(), Constants.MAX_CACHE_SIZE));
        return client;
    });

    private final Lazy<Database> database = new Lazy<>(() -> new Database(App.this, Constants.DB_NAME, Constants.DB_VERSION));
    private final Lazy<Broker> broker = new Lazy<>(() -> new Broker(providesDatabase()));
    private final Lazy<IAuthService> authService = new Lazy<>(signalService::get);

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
    public ISignalService providesSignalService() {
        return signalService.get();
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
    public IAuthService providesAuthService() {
        return authService.get();
    }
}
