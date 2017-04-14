package com.xianzhitech.ptt.engine

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import cn.netptt.engine.VoiceEngine
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.service.VoiceService
import com.xianzhitech.ptt.service.VoiceServiceJoinRoomRequest
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress

/**

 * WebRTC引擎实例

 * Created by fanchao on 13/12/15.
 */
class WebRtcTalkEngine(context: Context,
                       val httpClient: OkHttpClient)  {

    private val handler: Handler
    private val context: Context

    private lateinit var mediaEngine: VoiceEngine
    private var channel : Int = -1
    private lateinit var voiceService : VoiceService
    private var extProtoData: IntArray? = null

    init {
        this.handler = Handler(ENGINE_THREAD.looper)
        this.context = context.applicationContext
    }

    fun connect(roomId: String, property: Map<String, Any?>) {
        if (this.extProtoData != null) {
            throw IllegalStateException("Engine already connected to a room before")
        }

        val roomIdLong = roomId.toLong()
        this.extProtoData = intArrayOf((roomIdLong ushr 32).toInt(), (roomIdLong and 0xFFFFFFFF).toInt())

        handler.post {
            mediaEngine = VoiceEngine(context)

            channel = mediaEngine.createChannel(property[PROPERTY_PROTOCOL]?.equals("tcp") ?: false)

            if (channel < 0) {
                throw IllegalStateException("Unable to create channel: code = ${mediaEngine.lastErr}")
            }

            val userId = property[PROPERTY_LOCAL_USER_ID]?.toString()?.toInt()

            mediaEngine.setSendCodec(channel, mediaEngine.availableCodecs.first { it.name == "opus" })
            mediaEngine.setLocalSSRC(channel, userId ?: throw IllegalArgumentException("ContactUser id is null"))
            mediaEngine.setSendDestination(channel,
                    property[PROPERTY_REMOTE_SERVER_ADDRESS]?.toString()?.resolveToIPAddress() ?: throw IllegalArgumentException("No server ip specified"),
                    property[PROPERTY_REMOTE_SERVER_PORT]?.toString()?.toInt() ?: throw IllegalArgumentException("No report port specified"))

            mediaEngine.setLocalReceiver(channel, LOCAL_RTP_PORT, LOCAL_RTCP_PORT)
            mediaEngine.startListen(channel)
            mediaEngine.startPlayout(channel)

            voiceService = Retrofit.Builder()
                    .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(httpClient)
                    .baseUrl("http://${property[PROPERTY_REMOTE_SERVER_ADDRESS]}:${property[PROPERTY_REMOTE_SERVER_TCP_PORT]}/")
                    .build()
                    .create(VoiceService::class.java)

            voiceService.command(VoiceServiceJoinRoomRequest(roomId, userId.toString(), userId.toLong())).subscribeSimple()

            mediaEngine.sendExtPacket(channel, RTP_EXT_PROTO_JOIN_ROOM, extProtoData)
            try {
                Thread.sleep(100)
            } catch(e: Exception) {
            }
            mediaEngine.sendExtPacket(channel, RTP_EXT_PROTO_JOIN_ROOM, extProtoData)

            scheduleHeartbeat()
        }
    }

    private fun scheduleHeartbeat() {
        handler.postDelayed({
            mediaEngine.sendExtPacket(channel, RTP_EXT_PROTO_HEARTBEAT, extProtoData)
            scheduleHeartbeat()
        }, HEARTBEAT_INTERVAL_MILLS)
    }

    fun startSend() {
        handler.post { mediaEngine.startSend(channel) }
    }

    fun stopSend() {
        handler.post { mediaEngine.stopSend(channel) }
    }

    fun dispose() {
        handler.post {
            mediaEngine.sendExtPacket(channel, RTP_EXT_PROTO_QUIT_ROOM, extProtoData)
            mediaEngine.stopPlayout(channel)
            mediaEngine.stopListen(channel)
            mediaEngine.stopSend(channel)
            mediaEngine.destroyChannel(channel)
            mediaEngine.destroy()
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
        const val PROPERTY_REMOTE_SERVER_TCP_PORT = "server_tcp_port"
        const val PROPERTY_PROTOCOL = "protocol"
        const val PROPERTY_LOCAL_USER_ID = "local_user_id"

        private val LOCAL_RTP_PORT = 0
        private val LOCAL_RTCP_PORT = 0

        private val ENGINE_THREAD = HandlerThread("RTCEngineThread").apply { start() }
    }
}
