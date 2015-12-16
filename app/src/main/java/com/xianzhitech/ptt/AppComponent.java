package com.xianzhitech.ptt;

import com.squareup.okhttp.OkHttpClient;
import com.xianzhitech.ptt.engine.ITalkEngineFactory;
import com.xianzhitech.ptt.service.auth.IAuthService;
import com.xianzhitech.ptt.service.signal.ISignalService;

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
