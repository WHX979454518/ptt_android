package com.xianzhitech.ptt.engine

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import cn.netptt.engine.VoiceEngine
import com.xianzhitech.ptt.ext.e
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.print
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.service.VoiceService
import com.xianzhitech.ptt.service.VoiceServiceJoinRoomRequest
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
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
    private var mediaChannel : Int? = null
    private lateinit var voiceService : VoiceService
    private var extProtoData: IntArray? = null
    var highQualityMode : Boolean = false
    private set

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
            logger.i { "Available codecs: ${mediaEngine.availableCodecs.print()}" }

            mediaChannel = mediaEngine.createChannel(property[PROPERTY_PROTOCOL] == "tcp")

            applySoundQuality()

            logger.i { "Current codec set to: ${mediaEngine.getSendCodec(mediaChannel!!)}" }

            val userId = property[PROPERTY_LOCAL_USER_ID]?.toString()?.toInt()
            if (!mediaEngine.setLocalSSRC(mediaChannel!!, userId ?: throw IllegalArgumentException("User id is null"))) {
                logger.e { "Error setting local ssrc: ${mediaEngine.lastErr}" }
            }
            if (!mediaEngine.setSendDestination(mediaChannel!!,
                    property[PROPERTY_REMOTE_SERVER_ADDRESS]?.toString()?.resolveToIPAddress() ?: throw IllegalArgumentException("No server ip specified"),
                    property[PROPERTY_REMOTE_SERVER_PORT]?.toString()?.toInt() ?: throw IllegalArgumentException("No report port specified"))) {
                logger.e { "Error setting dest ssrc: ${mediaEngine.lastErr}" }
            }
            if (!mediaEngine.setLocalReceiver(mediaChannel!!, LOCAL_RTP_PORT, LOCAL_RTCP_PORT)) {
                logger.e { "Error setting local local receiver: ${mediaEngine.lastErr}" }
            }

            if (!mediaEngine.startListen(mediaChannel!!)) {
                logger.e { "Error start listening: ${mediaEngine.lastErr}" }
            }

            if (!mediaEngine.startPlayout(mediaChannel!!)) {
                logger.e { "Error start playing out: ${mediaEngine.lastErr}" }
            }

            voiceService = Retrofit.Builder()
                    .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(httpClient)
                    .baseUrl("http://${property[PROPERTY_REMOTE_SERVER_ADDRESS]}:${property[PROPERTY_REMOTE_SERVER_TCP_PORT]}/")
                    .build()
                    .create(VoiceService::class.java)

            voiceService.command(VoiceServiceJoinRoomRequest(roomId, userId.toString(), userId.toLong())).subscribeSimple()

            if (!mediaEngine.sendExtPacket(mediaChannel!!, RTP_EXT_PROTO_JOIN_ROOM, extProtoData)) {
                logger.e { "Error sending join packet: ${mediaEngine.lastErr}" }
            }

            try {
                Thread.sleep(100)
            } catch(e: Exception) {
            }

            if (!mediaEngine.sendExtPacket(mediaChannel!!, RTP_EXT_PROTO_JOIN_ROOM, extProtoData)) {
                logger.e { "Error sending join packet: ${mediaEngine.lastErr}" }
            }

            scheduleHeartbeat()
        }
    }

    private fun applySoundQuality() {
        if (!mediaEngine.setSendCodec(mediaChannel!!, if (highQualityMode) HIGH_QUALITY_CODEC else LOW_QUALITY_CODEC)) {
            logger.e { "Error setting codec: ${mediaEngine.lastErr}" }
        }
    }

    private fun scheduleHeartbeat() {
        handler.postDelayed({
            mediaEngine.sendExtPacket(mediaChannel!!, RTP_EXT_PROTO_HEARTBEAT, extProtoData)
            scheduleHeartbeat()
        }, HEARTBEAT_INTERVAL_MILLS)
    }

    fun enableHighQualityMode(enable : Boolean) {
        if (highQualityMode != enable) {
            highQualityMode = enable

            handler.post {
                if (mediaChannel != null) {
                    applySoundQuality()
                }
            }
        }
    }

    fun startSend() {
        handler.post { mediaEngine.startSend(mediaChannel!!) }
    }

    fun stopSend() {
        handler.post { mediaEngine.stopSend(mediaChannel!!) }
    }

    fun dispose() {
        handler.post {
            mediaEngine.sendExtPacket(mediaChannel!!, RTP_EXT_PROTO_QUIT_ROOM, extProtoData)
            mediaEngine.stopSend(mediaChannel!!)
            mediaEngine.stopListen(mediaChannel!!)
            mediaEngine.destroy()
            mediaChannel = null
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

        private val HIGH_QUALITY_CODEC = VoiceEngine.CodecInst("opus", 64000, 960, 2, 48000)
        private val LOW_QUALITY_CODEC = VoiceEngine.CodecInst("opus", 8000, 960, 1, 48000)

        private val logger = LoggerFactory.getLogger("TalkEngine")
        private val ENGINE_THREAD = HandlerThread("RTCEngineThread").apply { start() }
    }
}
