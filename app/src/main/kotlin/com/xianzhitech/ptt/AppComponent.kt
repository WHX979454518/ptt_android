package com.xianzhitech.ptt

import com.squareup.okhttp.OkHttpClient
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.presenter.ConversationPresenter
import com.xianzhitech.ptt.presenter.LoginPresenter
import com.xianzhitech.ptt.service.provider.AuthProvider
import com.xianzhitech.ptt.service.provider.PreferenceStorageProvider
import com.xianzhitech.ptt.service.provider.SignalProvider

/**

 * 应用程序组件. 提供各种库依赖

 * Created by fanchao on 13/12/15.
 */
interface AppComponent {
    val httpClient: OkHttpClient
    val signalProvider: SignalProvider
    val talkEngineProvider: TalkEngineProvider
    val broker: Broker
    val authProvider: AuthProvider
    val preferenceProvider: PreferenceStorageProvider

    // Presenters
    val loginPresenter: LoginPresenter
    val conversationPresenter: ConversationPresenter
}
