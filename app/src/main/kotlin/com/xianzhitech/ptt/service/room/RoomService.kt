package com.xianzhitech.ptt.service.room

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.SoundPool
import android.os.Binder
import android.os.IBinder
import android.os.Vibrator
import android.support.annotation.RawRes
import android.util.SparseIntArray
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.engine.TalkEngine
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.RoomInfo
import com.xianzhitech.ptt.service.provider.SignalProvider
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.CompositeSubscription
import kotlin.collections.listOf

/**
 * 提供房间服务的查询接口
 */
interface RoomServiceBinder {
    val currentSpeakerId: String?
    val activeMemberIds: Collection<String>
    val roomStatus: RoomStatus
}

/**
 * 房间的当前状态
 */
enum class RoomStatus {
    /**
     * 房间未连接
     */
    NOT_CONNECTED,

    /**
     * 正在连接到房间
     */
    CONNECTING,

    /**
     * 房间已连接
     */
    CONNECTED,

    /**
     * 正在求Mic
     */
    REQUESTING_MIC,

    /**
     * 房间处于有别人讲话的状态
     */
    ACTIVE,
}

/**
 *
 * 提供房间/对讲支持
 *
 * Created by fanchao on 18/12/15.d
 */
class RoomService() : Service(), RoomServiceBinder {
    companion object {

        // --------- 以下是本服务接收的事件 -------------

        /**
         * 连接到一个会话. 会话ID以extra形式传入
         */
        public const val ACTION_CONNECT = "action_connect"

        /**
         * 会话ID的key
         */
        public const val EXTRA_CONVERSATION_ID = "extra_conv_id"

        /**
         * 退出一个会话
         */
        public const val ACTION_DISCONNECT = "action_disconnect"

        /**
         * 请求抢麦
         */
        public const val ACTION_REQUEST_FOCUS = "action_request_focus"


        /**
         * 请求释麦
         */
        public const val ACTION_RELEASE_FOCUS = "action_release_focus"

        // --------- 以下是本服务发出的事件 -------------

        /**
         * 当前持麦人发生变化时发出的事件
         */
        public const val ACTION_CURRENT_SPEAKER_CHANGED = "action_current_speaker_changed"

        /**
         * 在线成员发生变化的事件
         */
        public const val ACTION_ACTIVE_MEMBERS_CHANGED = "action_active_members_changed"

        /**
         * 房间连接状态发生改变时发出的事件
         */
        public const val ACTION_ROOM_STATUS_CHANGED = "action_room_status_changed"
        /**
         * 房间出错时发出的事件
         */
        public const val ACTION_CONNECT_ERROR = "action_connect_error"


        public const val ACTION_REQUEST_FOCUS_ERROR = "action_request_focus_error"

        // ---------- 以下是辅助函数 --------------------
        public @JvmStatic fun buildEmpty(context: Context) = Intent(context, RoomService::class.java)

        /**
         * 请求连接至会话
         */
        public @JvmStatic fun buildConnect(context: Context, conversationId: String) =
                buildEmpty(context).setAction(ACTION_CONNECT).putExtra(EXTRA_CONVERSATION_ID, conversationId)


        /**
         * 请求断开会话
         */
        public @JvmStatic fun buildDisconnect(context: Context) =
                buildEmpty(context).setAction(ACTION_DISCONNECT)

        /**
         * 求/放麦
         */
        public @JvmStatic fun buildRequestFocus(context: Context, hasFocus: Boolean) =
                buildEmpty(context).setAction(if (hasFocus) ACTION_REQUEST_FOCUS else ACTION_RELEASE_FOCUS)

        /**
         * 监听房间状态的变化
         */
        public @JvmStatic fun getRoomStatus(context: Context): Observable<RoomStatus> =
                context.retrieveServiceValue(buildEmpty(context), { binder: RoomServiceBinder -> binder.roomStatus }, true,
                        AndroidSchedulers.mainThread(), ACTION_ROOM_STATUS_CHANGED)

        /**
         * 监听当前正在讲话的用户的变化
         */
        public @JvmStatic fun getCurrentSpeakerId(context: Context): Observable<String?> =
                context.retrieveServiceValue(buildEmpty(context), { binder: RoomServiceBinder -> binder.currentSpeakerId }, true,
                        AndroidSchedulers.mainThread(), ACTION_CURRENT_SPEAKER_CHANGED)

        /**
         * 监听当前在线用户的变化
         */
        public @JvmStatic fun getActiveMemberIds(context: Context): Observable<Collection<String>> =
                context.retrieveServiceValue(buildEmpty(context), { binder: RoomServiceBinder -> binder.activeMemberIds }, true,
                        AndroidSchedulers.mainThread(), ACTION_ACTIVE_MEMBERS_CHANGED)
    }

    private var binder: IBinder? = null

    private val isCurrentSpeakerNotLogonUser: Boolean
        get() = currentSpeakerId != null && currentSpeakerId != logonUserId

    override var currentSpeakerId: String? = null
        set(value) {
            if (field != value) {
                val wasCurrentSpeakerNotLogonUser = isCurrentSpeakerNotLogonUser
                field = value
                if (isCurrentSpeakerNotLogonUser) {
                    // 其他对讲用户语音开始
                    playSound(R.raw.incoming)
                } else if (value == null && wasCurrentSpeakerNotLogonUser) {
                    // 其他对讲用户语音结束
                    playSound(R.raw.over)
                }
                sendLocalBroadcast(Intent(ACTION_CURRENT_SPEAKER_CHANGED))
                roomStatus = if (value == null) RoomStatus.CONNECTED else RoomStatus.ACTIVE
            }
        }

    override var activeMemberIds: Collection<String> = listOf()
        set(value) {
            field = value
            sendLocalBroadcast(Intent(ACTION_ACTIVE_MEMBERS_CHANGED))
        }

    override var roomStatus = RoomStatus.NOT_CONNECTED
        set(value) {
            if (field != value) {
                field = value
                logd("RoomStatus changed to $value")
                sendLocalBroadcast(Intent(ACTION_ROOM_STATUS_CHANGED))
            }
        }
    lateinit var signalProvider: SignalProvider
    lateinit var talkEngineProvider: TalkEngineProvider
    lateinit var logonUserId: String
    var currentConversationId: String? = null
    var currentTalkEngine: TalkEngine? = null

    var currentRoomInfo: RoomInfo? = null
        set(value) {
            field = value
            currentSpeakerId = value?.speaker
        }
    var connectSubscription: Subscription? = null
    var requestFocusSubscription: Subscription? = null
    var soundPool: Pair<SoundPool, SparseIntArray> = Pair(SoundPool(1, AudioManager.STREAM_RING, 0), SparseIntArray())
    var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()

        val appComponent = application as AppComponent
        signalProvider = appComponent.signalProvider
        talkEngineProvider = appComponent.talkEngineProvider
        logonUserId = appComponent.authProvider.peekCurrentLogonUserId() ?: throw IllegalArgumentException("No logon user")

        soundPool.second.put(R.raw.incoming, soundPool.first.load(this, R.raw.incoming, 0))
        soundPool.second.put(R.raw.outgoing, soundPool.first.load(this, R.raw.outgoing, 0))
        soundPool.second.put(R.raw.over, soundPool.first.load(this, R.raw.over, 0))
        soundPool.second.put(R.raw.pttup, soundPool.first.load(this, R.raw.pttup, 0))
        soundPool.second.put(R.raw.pttup_offline, soundPool.first.load(this, R.raw.pttup_offline, 0))

        vibrator = (getSystemService(VIBRATOR_SERVICE) as Vibrator).let { if (it.hasVibrator()) it else null }
    }

    override fun onDestroy() {
        doDisconnect()
        soundPool.first.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        handleIntent(intent)

        if (binder == null) {
            binder = LocalRoomServiceBinder(this)
        }
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleIntent(intent)
        return START_NOT_STICKY
    }

    private fun handleIntent(intent: Intent?) {
        intent?.apply {
            when (action) {
                ACTION_CONNECT -> doConnect(getStringExtra(EXTRA_CONVERSATION_ID))
                ACTION_DISCONNECT -> doDisconnect()
                ACTION_REQUEST_FOCUS -> doRequestFocus()
                ACTION_RELEASE_FOCUS -> doReleaseFocus()
            }
        }
    }

    private fun playSound(@RawRes soundRes: Int) {
        soundPool.first.play(soundPool.second[soundRes], 1f, 1f, 1, 0, 1f)
    }

    private fun doConnect(conversationId: String) {
        if (conversationId == currentConversationId) {
            logd("Conversation $conversationId already connected")
            return
        }

        // Remove any previously connected conversation
        if (currentConversationId != null) {
            disposeTalkEngine()
            cancelRequestFocus()
            cancelConnect()
        }

        roomStatus = RoomStatus.CONNECTING

        currentConversationId = conversationId
        connectSubscription = CompositeSubscription().apply {
            add(signalProvider.joinConversation(conversationId)
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<RoomInfo>(this@RoomService) {
                        override fun onError(e: Throwable) {
                            sendBroadcast(Intent(ACTION_CONNECT_ERROR))
                            roomStatus = RoomStatus.NOT_CONNECTED
                        }

                        override fun onNext(t: RoomInfo) {
                            currentRoomInfo = t
                            if (currentTalkEngine == null) {
                                currentTalkEngine = talkEngineProvider.createEngine().apply { connect(t) }
                            }
                            roomStatus = RoomStatus.CONNECTED
                        }
                    }))

            add(signalProvider.getCurrentSpeakerId(conversationId)
                    .observeOnMainThread()
                    .subscribe {
                        currentSpeakerId = it
                    })

            add(signalProvider.getActiveMemberIds(conversationId)
                    .observeOnMainThread()
                    .subscribe {
                        activeMemberIds = it
                    })
        }
    }

    private fun doDisconnect() {
        currentConversationId?.let {
            disposeTalkEngine()
            cancelRequestFocus()
            cancelConnect()

            signalProvider.quitConversation(it)
                    .observeOnMainThread()
                    .subscribe(GlobalSubscriber<Unit>(this))

            roomStatus = RoomStatus.NOT_CONNECTED
        }

        currentConversationId = null
        stopSelf()
    }


    private fun doRequestFocus() {
        currentRoomInfo?.let {
            if (roomStatus == RoomStatus.CONNECTED) {
                roomStatus = RoomStatus.REQUESTING_MIC

                requestFocusSubscription = signalProvider.requestMic(it.id)
                        .observeOnMainThread()
                        .subscribe(object : GlobalSubscriber<Boolean>(this@RoomService) {
                            override fun onError(e: Throwable) {
                                sendBroadcast(Intent(ACTION_REQUEST_FOCUS_ERROR))
                            }

                            override fun onNext(t: Boolean) {
                                if (t) {
                                    currentSpeakerId = logonUserId
                                    currentTalkEngine?.startSend()
                                    roomStatus = RoomStatus.ACTIVE
                                    playSound(R.raw.outgoing)
                                    vibrator?.vibrate(100)
                                }
                            }
                        })
            }
        }
    }

    private fun doReleaseFocus() {
        currentRoomInfo?.let {
            if ((roomStatus == RoomStatus.ACTIVE && currentSpeakerId == logonUserId) ||
                    roomStatus == RoomStatus.REQUESTING_MIC) {
                cancelRequestFocus()
                currentTalkEngine?.stopSend()
                signalProvider.releaseMic(it.id)
                        .observeOnMainThread()
                        .subscribe(GlobalSubscriber<Unit>(this))

                playSound(R.raw.pttup)

                currentSpeakerId = null
                roomStatus = RoomStatus.CONNECTED
            }
        }
    }

    private fun disposeTalkEngine() {
        currentTalkEngine?.apply {
            stopSend()
            dispose()
        }
        currentTalkEngine = null
    }

    private fun cancelRequestFocus() {
        requestFocusSubscription?.unsubscribe()
        requestFocusSubscription = null
    }

    private fun cancelConnect() {
        connectSubscription?.unsubscribe()
        connectSubscription = null
    }

}
private class LocalRoomServiceBinder(val service: RoomService) : Binder(), RoomServiceBinder by service {

}
