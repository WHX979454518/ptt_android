package com.xianzhitech.ptt.engine

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import org.webrtc.autoim.MediaEngine
import org.webrtc.autoim.NativeWebRtcContextRegistry
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**

 * WebRTC引擎实例

 * Created by fanchao on 13/12/15.
 */
class WebRtcTalkEngine(context: Context)  {

    private val handler: Handler
    private val context: Context

    private lateinit var mediaEngine: MediaEngine
    private var extProtoData: IntArray? = null

    init {
        this.handler = Handler(ENGINE_THREAD.looper)
        this.context = context.applicationContext

        if (hasRegisteredWebRtc.compareAndSet(false, true)) {
            handler.post {
                NativeWebRtcContextRegistry().apply { register(this@WebRtcTalkEngine.context) }
            }
        }
    }

    fun connect(roomId: String, property: Map<String, Any?>) {
        if (this.extProtoData != null) {
            throw IllegalStateException("Engine already connected to a room before")
        }

        val roomIdLong = roomId.toLong()
        this.extProtoData = intArrayOf((roomIdLong ushr 32).toInt(), (roomIdLong and 0xFFFFFFFF).toInt())

        handler.post {
            mediaEngine = MediaEngine(context, property[PROPERTY_PROTOCOL]?.equals("tcp") ?: false)
            mediaEngine.setLocalSSRC(property[PROPERTY_LOCAL_USER_ID]?.toString()?.toInt() ?: throw IllegalArgumentException("User id is null"))
            mediaEngine.setRemoteIp(property[PROPERTY_REMOTE_SERVER_ADDRESS]?.toString()?.resolveToIPAddress() ?: throw IllegalArgumentException("No server ip specified"))
            mediaEngine.setAudioTxPort(property[PROPERTY_REMOTE_SERVER_PORT]?.toString()?.toInt() ?: throw IllegalArgumentException("No report port specified"))
            mediaEngine.setAudioRxPort(LOCAL_RTP_PORT, LOCAL_RTCP_PORT)
            mediaEngine.setAgc(true)
            mediaEngine.setNs(true)
            mediaEngine.setEc(true)
            mediaEngine.setAudio(true)
            mediaEngine.start()
            mediaEngine.sendExtPacket(RTP_EXT_PROTO_JOIN_ROOM, extProtoData)

            scheduleHeartbeat()
        }
    }

    private fun scheduleHeartbeat() {
        handler.postDelayed({
            mediaEngine.sendExtPacket(RTP_EXT_PROTO_HEARTBEAT, extProtoData)
            scheduleHeartbeat()
        }, HEARTBEAT_INTERVAL_MILLS)
    }

    fun startSend() {
        handler.post { mediaEngine.startSend() }
    }

    fun stopSend() {
        handler.post { mediaEngine.stopSend() }
    }

    fun dispose() {
        handler.post {
            mediaEngine.sendExtPacket(RTP_EXT_PROTO_QUIT_ROOM, extProtoData)
            mediaEngine.stop()
            mediaEngine.dispose()
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun String.resolveToIPAddress() = InetAddress.getByName(this).hostAddress

    companion object {
        private val RTP_EXT_PROTO_JOIN_ROOM: Short = 300
        private val RTP_EXT_PROTO_QUIT_ROOM: Short = 301
        private val RTP_EXT_PROTO_HEARTBEAT: Short = 302

        private val HEARTBEAT_INTERVAL_MILLS: Long = 5000

        const val PROPERTY_REMOTE_SERVER_ADDRESS = "server_address"
        const val PROPERTY_REMOTE_SERVER_PORT = "server_port"
        const val PROPERTY_PROTOCOL = "protocol"
        const val PROPERTY_LOCAL_USER_ID = "local_user_id"

        private val LOCAL_RTP_PORT = 0
        private val LOCAL_RTCP_PORT = 0

        private val ENGINE_THREAD = HandlerThread("RTCEngineThread").apply { start() }
        private val hasRegisteredWebRtc = AtomicBoolean(false)
    }
}
