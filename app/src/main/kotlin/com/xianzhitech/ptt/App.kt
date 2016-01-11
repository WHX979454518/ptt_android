package com.xianzhitech.ptt

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.SoundPool
import android.os.Vibrator
import android.preference.PreferenceManager
import android.support.annotation.RawRes
import android.util.SparseIntArray
import com.squareup.okhttp.OkHttpClient
import com.squareup.picasso.OkHttpDownloader
import com.squareup.picasso.Picasso
import com.xianzhitech.ptt.db.AndroidDatabase
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.fromBase64ToSerializable
import com.xianzhitech.ptt.ext.serializeToBase64
import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.presenter.LoginPresenter
import com.xianzhitech.ptt.presenter.RoomPresenter
import com.xianzhitech.ptt.presenter.RoomPresenterView
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
        SocketIOProvider(userRepository, groupRepository, conversationRepository, contactRepository, "http://192.168.1.128:3000/")

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
    override val authProvider by lazy { signalProvider as AuthProvider }
    override val loginPresenter by lazy { LoginPresenter(authProvider, userRepository, preferenceProvider) }
    override val preferenceProvider: PreferenceStorageProvider by lazy { SharedPreferenceProvider(PreferenceManager.getDefaultSharedPreferences(this)) }
    override val roomPresenter: RoomPresenter by lazy {
        RoomPresenter(signalProvider, authProvider, talkEngineProvider, userRepository, conversationRepository).apply {
            attachView(GlobalRoomPresenterView(this@App, authProvider))
        }
    }

    override fun onCreate() {
        super.onCreate()

        Picasso.setSingletonInstance(
                Picasso.Builder(this).downloader(OkHttpDownloader(httpClient)).build())
    }

    private class GlobalRoomPresenterView(context: Context,
                                          private val authProvider: AuthProvider) : RoomPresenterView {
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

        override fun promptCurrentJoinedRoomIsImportant(currentRoom: Conversation) {
        }

        override fun promptConfirmSwitchingRoom(newRoom: Conversation) {
        }

        override fun onRoomQuited(conversation: Conversation?) {
        }

        override fun showRoom(room: Conversation) {
        }

        override fun showRequestingMic(isRequesting: Boolean) {
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

        override fun showRoomMembers(members: List<Person>, activeMemberIds: Collection<String>) {
        }

        override fun showLoading(visible: Boolean) {
        }

        override fun showError(err: Throwable) {
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
