package com.xianzhitech.ptt

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.squareup.okhttp.OkHttpClient
import com.squareup.picasso.OkHttpDownloader
import com.squareup.picasso.Picasso
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.fromBase64ToSerializable
import com.xianzhitech.ptt.ext.serializeToBase64
import com.xianzhitech.ptt.presenter.ConversationListPresenter
import com.xianzhitech.ptt.presenter.LoginPresenter
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.ConversationRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.LocalRepository
import com.xianzhitech.ptt.service.provider.AuthProvider
import com.xianzhitech.ptt.service.provider.PreferenceStorageProvider
import com.xianzhitech.ptt.service.provider.SignalProvider
import com.xianzhitech.ptt.service.provider.SocketIOProvider
import java.io.Serializable


open class App : Application(), AppComponent {
    override val httpClient by lazy { OkHttpClient() }
    override val signalProvider: SignalProvider  by lazy {
        SocketIOProvider(userRepository, groupRepository, conversationRepository, contactRepository, "http://61.157.38.95:9001/")

    }
    override val talkEngineProvider = object : TalkEngineProvider {
        override fun createEngine() = WebRtcTalkEngine(this@App)
    }

    override val userRepository by lazy { LocalRepository(Database(this@App, Constants.DB_NAME, Constants.DB_VERSION)) }
    override val groupRepository: GroupRepository
        get() = userRepository
    override val conversationRepository: ConversationRepository
        get() = userRepository
    override val contactRepository: ContactRepository
        get() = contactRepository
    override val authProvider by lazy { signalProvider as AuthProvider }
    override val loginPresenter by lazy { LoginPresenter(authProvider, preferenceProvider) }
    override val preferenceProvider: PreferenceStorageProvider by lazy { SharedPreferenceProvider(PreferenceManager.getDefaultSharedPreferences(this)) }
    val conversationListPresenter: ConversationListPresenter by lazy { ConversationListPresenter(conversationRepository) }

    override fun onCreate() {
        super.onCreate()

        Picasso.setSingletonInstance(
                Picasso.Builder(this).downloader(OkHttpDownloader(httpClient)).build())
    }

    private class SharedPreferenceProvider(private val pref: SharedPreferences) : PreferenceStorageProvider {
        override fun save(key: String, value: Serializable?) {
            pref.edit().putString(key, value?.serializeToBase64()).apply()
        }

        override fun remove(key: String) {
            pref.edit().remove(key).apply()
        }

        override fun get(key: String) = pref.getString(key, null)?.fromBase64ToSerializable()
    }
}
