package com.xianzhitech.ptt

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.SoundPool
import android.os.Vibrator
import android.preference.PreferenceManager
import android.support.annotation.RawRes
import android.util.SparseIntArray
import com.squareup.okhttp.OkHttpClient
import com.xianzhitech.ptt.db.AndroidDatabase
import com.xianzhitech.ptt.engine.BtEngine
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.fromBase64ToSerializable
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.serializeToBase64
import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.presenter.BaseRoomPresenterView
import com.xianzhitech.ptt.presenter.LoginPresenter
import com.xianzhitech.ptt.presenter.RoomPresenter
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.ConversationRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.LocalRepository
import com.xianzhitech.ptt.service.provider.AuthProvider
import com.xianzhitech.ptt.service.provider.ConversationFromExisting
import com.xianzhitech.ptt.service.provider.PreferenceStorageProvider
import com.xianzhitech.ptt.service.provider.SocketIOProvider
import com.xianzhitech.ptt.ui.service.BackgroundService
import java.io.Serializable


open class App : Application(), AppComponent {
    override val httpClient by lazy { OkHttpClient() }
    override val signalProvider by lazy {
        SocketIOProvider(userRepository, groupRepository, conversationRepository, contactRepository, BuildConfig.SIGNAL_SERVER_ENDPOINT)
    }
    override val talkEngineProvider = object : TalkEngineProvider {
        override fun createEngine() = WebRtcTalkEngine(this@App)
    }

    override val userRepository by lazy { LocalRepository(AndroidDatabase(this@App, Constants.DB_NAME, Constants.DB_VERSION)) }
    override val groupRepository: GroupRepository
        get() = userRepository
    override val conversationRepository: ConversationRepository
        get() = userRepository
    override val contactRepository: ContactRepository
        get() = userRepository
    override val authProvider by lazy { signalProvider }
    override val loginPresenter by lazy { LoginPresenter(authProvider, preferenceProvider) }
    override val preferenceProvider: PreferenceStorageProvider by lazy { SharedPreferenceProvider(PreferenceManager.getDefaultSharedPreferences(this)) }
    override val roomPresenter: RoomPresenter by lazy {
        RoomPresenter(signalProvider, authProvider, talkEngineProvider, userRepository, conversationRepository).apply {
            attachView(GlobalRoomPresenterView(this@App, btEngine, authProvider))
        }
    }
    override val btEngine by lazy { BtEngine(this) }

    override val backgroundRoomPresenterView = object : BaseRoomPresenterView() {
        override fun onRoomJoined(conversationId: String) {
            // 有房间加入, 拉起对讲界面
            startService(Intent(this@App, BackgroundService::class.java)
                    .setAction(BackgroundService.ACTION_OPEN_ROOM)
                    .putExtra(BackgroundService.EXTRA_CONVERSATION_REQUEST, ConversationFromExisting(conversationId)))
        }
    }

    override fun onCreate() {
        super.onCreate()

        //        Picasso.setSingletonInstance(
        //                Picasso.Builder(this).downloader(OkHttpDownloader(httpClient)).build())

        // Attach background processor to room presenter
        roomPresenter.attachView(backgroundRoomPresenterView)

        //FIXME: Here we force initialization to do dependency injection
        signalProvider.roomPresenter = roomPresenter

        // Hook up bluetooth engine
        btEngine.btMicEnable
                .first { it == true }
                .flatMap { btEngine.btMessage }
                .observeOnMainThread()
                .subscribe(object : GlobalSubscriber<String>() {
                    override fun onNext(t: String) {
                        when (t) {
                            BtEngine.MESSAGE_PUSH_DOWN -> roomPresenter.requestMic()
                            BtEngine.MESSAGE_PUSH_RELEASE -> roomPresenter.releaseMic()
                        }
                    }
                })

    }

    private class GlobalRoomPresenterView(context: Context,
                                          private val btEngine: BtEngine,
                                          private val authProvider: AuthProvider) : BaseRoomPresenterView() {
        private var lastSpeaker: Person? = null

        var soundPool: Pair<SoundPool, SparseIntArray> = Pair(SoundPool(1, AudioManager.STREAM_MUSIC, 0), SparseIntArray()).apply {
            second.put(R.raw.incoming, first.load(context, R.raw.incoming, 0))
            second.put(R.raw.outgoing, first.load(context, R.raw.outgoing, 0))
            second.put(R.raw.over, first.load(context, R.raw.over, 0))
            second.put(R.raw.pttup, first.load(context, R.raw.pttup, 0))
            second.put(R.raw.pttup_offline, first.load(context, R.raw.pttup_offline, 0))
        }

        var vibrator = (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).let {
            if (it.hasVibrator()) it
            else null
        }

        override fun onRoomJoined(conversationId: String) {
            super.onRoomJoined(conversationId)

            btEngine.startSCO()
        }

        override fun onRoomQuited(conversation: Conversation?) {
            super.onRoomQuited(conversation)

            btEngine.stopSCO()
        }

        override fun showCurrentSpeaker(speaker: Person?, isSelf: Boolean) {
            if (speaker != null && isSelf) {
                playSound(R.raw.outgoing)
                vibrator?.vibrate(100)
            } else if (speaker == null && lastSpeaker == authProvider.peekCurrentLogonUser()) {
                playSound(R.raw.pttup)
            } else if (speaker != null && !isSelf) {
                playSound(R.raw.incoming)
            } else if (speaker == null && lastSpeaker != null) {
                playSound(R.raw.over)
            }

            lastSpeaker = speaker
        }

        private fun playSound(@RawRes res: Int) {
            soundPool.first.play(soundPool.second[res], 1f, 1f, 1, 0, 1f)
        }
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
