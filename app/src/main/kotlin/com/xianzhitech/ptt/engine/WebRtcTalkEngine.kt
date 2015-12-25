package com.xianzhitech.ptt.engine

import android.content.Context
import android.os.Handler
import android.os.HandlerThread

import com.xianzhitech.ptt.model.Room

import org.webrtc.autoim.MediaEngine

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
    }

    override fun connect(room: Room) {
        if (this.roomIds != null) {
            throw IllegalStateException("Engine already connected to a room before")
        }

        val roomId = room.id.toInt()
        this.roomIds = intArrayOf(roomId)

        handler.post {
            mediaEngine = MediaEngine(context, false)
            mediaEngine.setLocalSSRC(roomId)
            mediaEngine.setRemoteIp(room.serverHost)
            mediaEngine.setAudioTxPort(room.severPort)
            mediaEngine.setAudioRxPort(LOCAL_RTP_PORT, LOCAL_RTCP_PORT)
            mediaEngine.setAgc(true)
            mediaEngine.setNs(true)
            mediaEngine.setEc(true)
            mediaEngine.setSpeaker(false)
            mediaEngine.setAudio(true)
            mediaEngine.start(roomId)
            mediaEngine.sendExtPacket(RTP_EXT_PROTO_JOIN_ROOM, roomIds)

            scheduleHeartbeat()
        }
    }

    private fun scheduleHeartbeat() {
        handler.postDelayed(Runnable {
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
            mediaEngine.dispose()
            handler.removeCallbacksAndMessages(null)
        }
    }

    companion object {
        private val RTP_EXT_PROTO_JOIN_ROOM: Short = 300
        private val RTP_EXT_PROTO_QUIT_ROOM: Short = 301
        private val RTP_EXT_PROTO_HEARTBEAT: Short = 302

        private val HEARTBEAT_INTERVAL_MILLS: Long = 5000

        private val LOCAL_RTP_PORT = 10010
        private val LOCAL_RTCP_PORT = 10011

        private val ENGINE_THREAD = object : HandlerThread("RTCEngineThread") {
            init {
                start()
            }
        }
    }
}
