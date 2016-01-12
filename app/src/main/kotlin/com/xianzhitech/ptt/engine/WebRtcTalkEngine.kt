package com.xianzhitech.ptt.engine

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import org.webrtc.autoim.MediaEngine
import org.webrtc.autoim.NativeWebRtcContextRegistry
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.text.toInt

/**

 * WebRTC引擎实例

 * Created by fanchao on 13/12/15.
 */
class WebRtcTalkEngine(context: Context) : TalkEngine {

    private val handler: Handler
    private val context: Context
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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

    override fun connect(roomId: String, property: Map<String, Any?>) {
        if (this.roomIds != null) {
            throw IllegalStateException("Engine already connected to a room before")
        }

        val roomIdInt = roomId.toInt()
        this.roomIds = intArrayOf(roomIdInt)

        handler.post {
            mediaEngine = MediaEngine(context, property[PROPERTY_PROTOCOL]?.equals("tcp") ?: false)
            mediaEngine.setLocalSSRC(property[PROPERTY_LOCAL_USER_ID]?.toString()?.toInt() ?: throw IllegalArgumentException("User id is null"))
            mediaEngine.setRemoteIp(property[PROPERTY_REMOTE_SERVER_IP]?.toString()?.resolveToIPAddress() ?: throw IllegalArgumentException("No server ip specified"))
            mediaEngine.setAudioTxPort(property[PROPERTY_REMOTE_SERVER_PORT]?.toString()?.toInt() ?: throw IllegalArgumentException("No report port specified"))
            mediaEngine.setAudioRxPort(LOCAL_RTP_PORT, LOCAL_RTCP_PORT)
            mediaEngine.setAgc(true)
            mediaEngine.setNs(true)
            mediaEngine.setEc(true)
            mediaEngine.setSpeaker(true)
            mediaEngine.setAudio(true)
            mediaEngine.start(roomIdInt)
            mediaEngine.sendExtPacket(RTP_EXT_PROTO_JOIN_ROOM, roomIds)

            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)

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

            audioManager.abandonAudioFocus(null)
        }
    }

    private fun String.resolveToIPAddress() = InetAddress.getByName(this).hostAddress

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
