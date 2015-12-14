package com.podkitsoftware.shoumi;

import com.podkitsoftware.shoumi.engine.TalkEngineFactory;
import com.podkitsoftware.shoumi.service.signal.SignalService;
import com.squareup.okhttp.OkHttpClient;

/**
 *
 * 应用程序组件. 提供各种库依赖
 *
 * Created by fanchao on 13/12/15.
 */
public interface AppComponent {
    OkHttpClient providesHttpClient();
    SignalService providesSignalService();
    TalkEngineFactory providesTalkEngineFactory();
    Database providesDatabase();
    Broker providesBroker();
}
