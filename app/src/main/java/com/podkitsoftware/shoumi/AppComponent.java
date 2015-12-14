package com.podkitsoftware.shoumi;

import com.podkitsoftware.shoumi.engine.ITalkEngineFactory;
import com.podkitsoftware.shoumi.service.auth.IAuthService;
import com.podkitsoftware.shoumi.service.signal.ISignalService;
import com.squareup.okhttp.OkHttpClient;

/**
 *
 * 应用程序组件. 提供各种库依赖
 *
 * Created by fanchao on 13/12/15.
 */
public interface AppComponent {
    OkHttpClient providesHttpClient();
    ISignalService providesSignalService();
    ITalkEngineFactory providesTalkEngineFactory();
    Database providesDatabase();
    Broker providesBroker();
    IAuthService providesAuthService();
}
