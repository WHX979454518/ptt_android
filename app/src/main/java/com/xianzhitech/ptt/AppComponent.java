package com.xianzhitech.ptt;

import android.support.annotation.NonNull;

import com.squareup.okhttp.OkHttpClient;
import com.xianzhitech.ptt.engine.TalkEngineProvider;
import com.xianzhitech.ptt.service.provider.AuthProvider;
import com.xianzhitech.ptt.service.provider.SignalProvider;

/**
 *
 * 应用程序组件. 提供各种库依赖
 *
 * Created by fanchao on 13/12/15.
 */
public interface AppComponent {
    @NonNull
    OkHttpClient providesHttpClient();

    @NonNull
    SignalProvider providesSignal();

    @NonNull
    TalkEngineProvider providesTalkEngine();

    @NonNull
    Database providesDatabase();

    @NonNull
    Broker providesBroker();

    @NonNull
    AuthProvider providesAuth();
}
