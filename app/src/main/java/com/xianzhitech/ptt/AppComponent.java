package com.xianzhitech.ptt;

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
    OkHttpClient providesHttpClient();

    SignalProvider providesSignal();

    TalkEngineProvider providesTalkEngine();
    Database providesDatabase();
    Broker providesBroker();
    AuthProvider providesAuth();
}
