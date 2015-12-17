package com.xianzhitech.ptt;

import com.squareup.okhttp.OkHttpClient;
import com.xianzhitech.ptt.engine.ITalkEngineFactory;
import com.xianzhitech.service.provider.AuthProvider;
import com.xianzhitech.service.provider.SignalProvider;

/**
 *
 * 应用程序组件. 提供各种库依赖
 *
 * Created by fanchao on 13/12/15.
 */
public interface AppComponent {
    OkHttpClient providesHttpClient();

    SignalProvider providesSignal();
    ITalkEngineFactory providesTalkEngineFactory();
    Database providesDatabase();
    Broker providesBroker();
    AuthProvider providesAuth();
}
