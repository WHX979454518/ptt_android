package com.podkitsoftware.shoumi;

import android.app.Application;

import com.podkitsoftware.shoumi.engine.TalkEngineFactory;
import com.podkitsoftware.shoumi.engine.WebRtcTalkEngine;
import com.podkitsoftware.shoumi.service.signal.SignalService;
import com.podkitsoftware.shoumi.service.signal.WebSocketSignalService;
import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.picasso.OkHttpDownloader;
import com.squareup.picasso.Picasso;

import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.apache.commons.lang3.concurrent.LazyInitializer;


public class App extends Application {

    private static App instance;

    private final LazyInitializer<SignalService> signalService = new LazyInitializer<SignalService>() {
        @Override
        protected SignalService initialize() throws ConcurrentException {
            return new WebSocketSignalService("", 80);
        }
    };

    private final LazyInitializer<TalkEngineFactory> talkEngineFactory = new LazyInitializer<TalkEngineFactory>() {
        @Override
        protected TalkEngineFactory initialize() throws ConcurrentException {
            return WebRtcTalkEngine::new;
        }
    };

    private final LazyInitializer<OkHttpClient> okHttpClient = new LazyInitializer<OkHttpClient>() {
        @Override
        protected OkHttpClient initialize() throws ConcurrentException {
            final OkHttpClient client = new OkHttpClient();
            client.setCache(new Cache(getCacheDir(), Constants.MAX_CACHE_SIZE));
            return client;
        }
    };

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
        try {
            return okHttpClient.get();
        } catch (ConcurrentException e) {
            throw new RuntimeException(e);
        }
    }

    public SignalService providesSignalServer() {
        try {
            return signalService.get();
        } catch (ConcurrentException e) {
            throw new RuntimeException(e);
        }
    }

    public TalkEngineFactory providesTalkEngineFactory() {
        try {
            return talkEngineFactory.get();
        } catch (ConcurrentException e) {
            throw new RuntimeException(e);
        }
    }

    public static App getInstance() {
        return instance;
    }
}
