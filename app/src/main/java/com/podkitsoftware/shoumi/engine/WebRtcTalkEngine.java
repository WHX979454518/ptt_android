package com.podkitsoftware.shoumi.engine;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import com.podkitsoftware.shoumi.model.Room;

import org.webrtc.autoim.MediaEngine;

/**
 *
 * WebRTC引擎实例
 *
 * Created by fanchao on 13/12/15.
 */
public class WebRtcTalkEngine implements TalkEngine {
    private static final short RTP_EXT_PROTO_JOIN_ROOM = 300;
    private static final short RTP_EXT_PROTO_QUIT_ROOM = 301;
    private static final short RTP_EXT_PROTO_HEARTBEAT = 302;

    private static final long HEARTBEAT_INTERVAL_MILLS = 5000;

    private static final int LOCAL_RTP_PORT = 10010;
    private static final int LOCAL_RTCP_PORT = 10011;

    private final Handler handler;
    private final Context context;

    MediaEngine mediaEngine;
    int[] roomIds;

    private static final HandlerThread ENGINE_THREAD = new HandlerThread("RTCEngineThread") {
        {
            start();
        }
    };

    public WebRtcTalkEngine(final Context context) {
        this.handler = new Handler(ENGINE_THREAD.getLooper());
        this.context = context.getApplicationContext();
    }

    @Override
    public void connect(final Room room) {
        if (this.roomIds != null) {
            throw new IllegalStateException("Engine already connected to a room before");
        }

        this.roomIds = new int[] { room.getRoomId() };

        handler.post(() -> {
            mediaEngine = new MediaEngine(context, false);
            mediaEngine.setLocalSSRC(room.getLocalUserId());
            mediaEngine.setRemoteIp(room.getRemoteServer());
            mediaEngine.setAudioTxPort(room.getRemotePort());
            mediaEngine.setAudioRxPort(LOCAL_RTP_PORT, LOCAL_RTCP_PORT);
            mediaEngine.setAgc(true);
            mediaEngine.setNs(true);
            mediaEngine.setEc(true);
            mediaEngine.setSpeaker(false);
            mediaEngine.setAudio(true);
            mediaEngine.start(room.getRoomId());
            mediaEngine.sendExtPacket(RTP_EXT_PROTO_JOIN_ROOM, roomIds);

            scheduleHeartbeat();
        });
    }

    private void scheduleHeartbeat() {
        handler.postDelayed(() -> {
            mediaEngine.sendExtPacket(RTP_EXT_PROTO_HEARTBEAT, roomIds);

            scheduleHeartbeat();
        }, HEARTBEAT_INTERVAL_MILLS);
    }

    @Override
    public void startSend() {
        handler.post(mediaEngine::startSend);
    }

    @Override
    public void stopSend() {
        handler.post(mediaEngine::stopSend);
    }

    @Override
    public void dispose() {
        handler.post(() -> {
            mediaEngine.sendExtPacket(RTP_EXT_PROTO_QUIT_ROOM, roomIds);
            mediaEngine.dispose();
            mediaEngine = null;
            handler.removeCallbacksAndMessages(null);
        });
    }
}
