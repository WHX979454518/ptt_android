package com.xianzhitech.ptt.engine

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.xianzhitech.ptt.model.RoomInfo
import org.webrtc.autoim.MediaEngine
import org.webrtc.autoim.NativeWebRtcContextRegistry
import java.util.concurrent.atomic.AtomicBoolean

/**

 * WebRTC引擎实例

 * Created by fanchao on 13/12/15.
 */
class WebRtcTalkEngine(context: Context) : TalkEngine {

    private val handler: Handler
    private val context: Context

    internal lateinit var mediaEngine: MediaEngine
    internal var roomIds: IntArray? = null

    init {
        this.handler = Handler(ENGINE_THREAD.looper)
        this.context = context.applicationContext

        if (hasRegisteredWebRtc.compareAndSet(false, true)) {
            handler.post {
                NativeWebRtcContextRegistry().apply { register(this@WebRtcTalkEngine.context) }
            }
        }
    }

    override fun connect(roomInfo: RoomInfo) {
        if (this.roomIds != null) {
            throw IllegalStateException("Engine already connected to a room before")
        }

        val roomId = roomInfo.id.toInt()
        this.roomIds = intArrayOf(roomId)

        handler.post {
            mediaEngine = MediaEngine(context, roomInfo.getProperty<String>(PROPERTY_PROTOCOL)?.equals("tcp") ?: false)
            mediaEngine.setLocalSSRC(roomInfo.getProperty<String>(PROPERTY_LOCAL_USER_ID)?.toInt() ?: throw IllegalArgumentException("User id is null"))
            mediaEngine.setRemoteIp(roomInfo.getProperty<String>(PROPERTY_REMOTE_SERVER_IP) ?: throw IllegalArgumentException("No server ip specified"))
            mediaEngine.setAudioTxPort(roomInfo.getProperty<Int>(PROPERTY_REMOTE_SERVER_PORT) ?: throw IllegalArgumentException("No report port specified"))
            mediaEngine.setAudioRxPort(LOCAL_RTP_PORT, LOCAL_RTCP_PORT)
            mediaEngine.setAgc(true)
            mediaEngine.setNs(true)
            mediaEngine.setEc(true)
            mediaEngine.setSpeaker(true)
            mediaEngine.setDebuging(true)
            mediaEngine.setAudio(true)
            mediaEngine.start(roomId)
            mediaEngine.sendExtPacket(RTP_EXT_PROTO_JOIN_ROOM, roomIds)

            scheduleHeartbeat()
        }
    }

    private fun scheduleHeartbeat() {
        handler.postDelayed({
            mediaEngine.sendExtPacket(RTP_EXT_PROTO_HEARTBEAT, roomIds)
            scheduleHeartbeat()
        }, HEARTBEAT_INTERVAL_MILLS)
    }

    override fun startSend() {
        handler.post { mediaEngine.startSend() }
    }

    override fun stopSend() {
        handler.post { mediaEngine.stopSend() }
    }

    override fun dispose() {
        handler.post {
            mediaEngine.sendExtPacket(RTP_EXT_PROTO_QUIT_ROOM, roomIds)
            mediaEngine.stop()
            mediaEngine.dispose()
            handler.removeCallbacksAndMessages(null)
        }
    }

    companion object {
        private val RTP_EXT_PROTO_JOIN_ROOM: Short = 300
        private val RTP_EXT_PROTO_QUIT_ROOM: Short = 301
        private val RTP_EXT_PROTO_HEARTBEAT: Short = 302

        private val HEARTBEAT_INTERVAL_MILLS: Long = 5000

        public const val PROPERTY_REMOTE_SERVER_IP = "server_ip"
        public const val PROPERTY_REMOTE_SERVER_PORT = "server_port"
        public const val PROPERTY_PROTOCOL = "protocol"
        public const val PROPERTY_LOCAL_USER_ID = "local_user_id"

        private val LOCAL_RTP_PORT = 0
        private val LOCAL_RTCP_PORT = 0

        private val ENGINE_THREAD = HandlerThread("RTCEngineThread").apply { start() }
        private val hasRegisteredWebRtc = AtomicBoolean(false)
    }
}
