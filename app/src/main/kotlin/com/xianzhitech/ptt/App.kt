package com.xianzhitech.ptt

import android.app.Application
import com.squareup.okhttp.OkHttpClient
import com.squareup.picasso.OkHttpDownloader
import com.squareup.picasso.Picasso
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.service.provider.AuthProvider
import com.xianzhitech.ptt.service.provider.SignalProvider
import com.xianzhitech.ptt.service.provider.SocketIOProvider


open class App : Application(), AppComponent {
    override val httpClient by lazy { OkHttpClient() }
    override val signalProvider: SignalProvider  by lazy { SocketIOProvider(broker, "http://106.186.124.143:3000/") }
    override val talkEngineProvider = object : TalkEngineProvider {
        override fun createEngine() = WebRtcTalkEngine(this@App)
    }
    override val broker by lazy { Broker(Database(this@App, Constants.DB_NAME, Constants.DB_VERSION)) }
    override val authProvider by lazy { signalProvider as AuthProvider }

    override fun onCreate() {
        super.onCreate()

        Picasso.setSingletonInstance(
                Picasso.Builder(this).downloader(OkHttpDownloader(httpClient)).build())
    }
}
