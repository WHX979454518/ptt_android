package com.xianzhitech.ptt.service.room

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.xianzhitech.ptt.ext.retrieveServiceValue
import com.xianzhitech.ptt.service.talk.RoomStatus
import rx.Observable

/**
 * 提供房间服务的查询接口
 */
interface RoomServiceBinder {
    val currentSpeakerId: String?
    val memberIds: List<String>

    @RoomStatus
    val roomStatus: Int
}

/**
 *
 * 提供房间/对讲支持
 *
 * Created by fanchao on 18/12/15.
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
         * 房间成员变化时发出的事件
         */
        public const val ACTION_MEMBER_CHANGED = "action_member_changed"

        /**
         * 房间连接状态发生改变时发出的事件
         */
        public const val ACTION_ROOM_STATUS_CHANGED = "action_room_status_changed"


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
        public @JvmStatic fun requestFocus(context: Context, hasFocus: Boolean) =
                buildEmpty(context).setAction(if (hasFocus) ACTION_REQUEST_FOCUS else ACTION_RELEASE_FOCUS)

        /**
         * 监听组成员的变化
         */
        public @JvmStatic fun getMemberIds(context: Context): Observable<List<String>> =
                context.retrieveServiceValue(buildEmpty(context), { binder: RoomServiceBinder -> binder.memberIds }, ACTION_MEMBER_CHANGED)

        /**
         * 监听房间状态的变化
         */
        public @JvmStatic fun getRoomStatus(context: Context): Observable<Int> =
                context.retrieveServiceValue(buildEmpty(context), { binder: RoomServiceBinder -> binder.roomStatus }, ACTION_ROOM_STATUS_CHANGED)

        /**
         * 监听当前正在讲话的用户的变化
         */
        public @JvmStatic fun getCurrentSpeakerId(context: Context): Observable<String?> =
                context.retrieveServiceValue(buildEmpty(context), { binder: RoomServiceBinder -> binder.currentSpeakerId }, ACTION_CURRENT_SPEAKER_CHANGED)

    }

    private var binder: IBinder? = null

    override var currentSpeakerId: String? = null
        set(value) {
            if (field != value) {
                field = value
                sendBroadcast(Intent(ACTION_CURRENT_SPEAKER_CHANGED))
            }
        }

    override var memberIds: List<String> = listOf()
        set(value) {
            if (field != value) {
                field = value
                sendBroadcast(Intent(ACTION_MEMBER_CHANGED))
            }
        }
    override var roomStatus = RoomStatus.NOT_CONNECTED
        set(value) {
            if (field != value) {
                field = value
                sendBroadcast(Intent(ACTION_ROOM_STATUS_CHANGED))
            }
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
        if (intent == null) {
            return;
        }
    }
}

private class LocalRoomServiceBinder(val service: RoomService) : Binder(), RoomServiceBinder by service {
}