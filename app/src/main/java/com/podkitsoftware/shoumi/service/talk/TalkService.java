package com.podkitsoftware.shoumi.service.talk;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.SparseArray;

import com.podkitsoftware.shoumi.BuildConfig;
import com.podkitsoftware.shoumi.model.Room;
import com.podkitsoftware.shoumi.util.Logger;

import org.apache.commons.lang3.ObjectUtils;
import org.webrtc.autoim.MediaEngine;

/**
 *
 * 对讲语音服务
 *
 * Created by fanchao on 13/12/15.
 */
public class TalkService extends Service {

    public static final String LOG_TAG = TalkService.class.getSimpleName();

    public static final String ACTION_ROOM_STATUS_CHANGED = "action_room_status_changed";
    public static final String ACTION_EXTRA_ROOM_STATUS = "extra_room_status";

    public static final String ACTION_CONNECT = "action_connect";
    public static final String ACTION_DISCONNECT = "action_disconnect";

    public static final String ACTION_EXTRA_ROOM_ID = "extra_room_id";
    public static final String ACTION_EXTRA_ROOM = "extra_room";

    private static final short RTP_EXT_PROTO_JOIN_ROOM = 300;
    private static final short RTP_EXT_PROTO_QUIT_ROOM = 301;
    private static final short RTP_EXT_PROTO_HEARTBEAT = 302;

    private static final long HEARTBEAT_INTERVAL_MILLS = 5000;

    private static final int LOCAL_RTP_PORT = 10010;
    private static final int LOCAL_RTCP_PORT = 10011;
    private static final int MSG_HEARTBEAT = 1;

    private final IBinder binder = new LocalBinder();
    @RoomStatus int roomStatus = TalkBinder.ROOM_STATUS_NOT_CONNECTED;
    @Nullable Room currRoom;
    private final SparseArray<MediaEngine> mediaEngines = new SparseArray<>();
    private final HandlerThread executionThread = new HandlerThread("TalkServiceCommand") {
        {
            start();
        }
    };
    private final Handler executionHandler = new Handler(executionThread.getLooper()) {
        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case MSG_HEARTBEAT: {
                    doHeartBeat((Integer) msg.obj);
                    break;
                }
            }
        }
    };
    private LocalBroadcastManager broadcastManager;
    private AudioManager audioManager;

    @Override
    public void onCreate() {
        super.onCreate();

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        broadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public void onDestroy() {
        broadcastManager = null;
        
        for (int i = 0, count = mediaEngines.size(); i < count; i++) {
            final int roomId = mediaEngines.keyAt(i);
            executionHandler.post(() -> doDisconnect(roomId, false));
        }
        
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(final Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (intent != null) {
            if (ACTION_CONNECT.equals(intent.getAction())) {
                executionHandler.post(() -> doConnect(intent.getParcelableExtra(ACTION_EXTRA_ROOM)));
            } else if (ACTION_DISCONNECT.equals(intent.getAction())) {
                executionHandler.post(() -> doDisconnect(intent.getIntExtra(ACTION_EXTRA_ROOM_ID, -1), true));
            } else {
                throw new IllegalArgumentException("Unknown intent: " + intent);
            }
        }

        return START_STICKY;
    }

    private void doHeartBeat(int roomId) {
        checkExecutionThread();

        final MediaEngine mediaEngine;

        if ((mediaEngine = mediaEngines.get(roomId)) == null) {
            Logger.d(LOG_TAG, "No current room. Stop heartbeat.");
            return;
        }

        mediaEngine.sendExtPacket(RTP_EXT_PROTO_HEARTBEAT, new int[]{roomId});
        scheduleHeartBeat(roomId);
    }

    private void scheduleHeartBeat(final int roomId) {
        executionHandler.sendMessageDelayed(executionHandler.obtainMessage(MSG_HEARTBEAT, roomId), HEARTBEAT_INTERVAL_MILLS);
    }

    private void doDisconnect(final int roomId, final boolean stopIfNoConnectionLeft) {
        checkExecutionThread();

        final MediaEngine mediaEngine = mediaEngines.get(roomId);
        if (mediaEngine == null) {
            Logger.d(LOG_TAG, "Room %s already disconnected", roomId);
            return;
        }

        mediaEngines.remove(roomId);

        final boolean isCurrentRoom = currRoom != null && roomId == currRoom.getRoomId();

        if (isCurrentRoom) {
            setCurrentRoomStatus(TalkBinder.ROOM_STATUS_DISCONNECTING);
        }

        Logger.d(LOG_TAG, "Disconnecting from room %s", roomId);
        mediaEngine.sendExtPacket(RTP_EXT_PROTO_QUIT_ROOM, new int[]{roomId});
        mediaEngine.dispose();

        if (isCurrentRoom) {
            audioManager.abandonAudioFocus(null);
            audioManager.setMode(AudioManager.MODE_NORMAL);
            setCurrentRoomStatus(TalkBinder.ROOM_STATUS_NOT_CONNECTED);
            currRoom = null;
        }

        executionHandler.removeMessages(MSG_HEARTBEAT, roomId);
        if (stopIfNoConnectionLeft && mediaEngines.size() == 0) {
            stopSelf();
        }
    }

    private void doConnect(final @NonNull Room room) {
        checkExecutionThread();

        if (ObjectUtils.equals(currRoom, room)) {
            Logger.d(LOG_TAG, "Room %s already connected", room);
            return;
        }

        if (currRoom != null) {
            doDisconnect(currRoom.getRoomId(), true);
        }

        currRoom = room;
        Logger.d(LOG_TAG, "Connecting to room %s", room);
        setCurrentRoomStatus(TalkBinder.ROOM_STATUS_CONNECTING);

        final MediaEngine mediaEngine = new MediaEngine(this, false);
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
        mediaEngine.sendExtPacket(RTP_EXT_PROTO_JOIN_ROOM, new int[]{room.getRoomId()});

        mediaEngines.put(room.getRoomId(), mediaEngine);
        setCurrentRoomStatus(TalkBinder.ROOM_STATUS_CONNECTED);

        // Fire a heartbeat event
        scheduleHeartBeat(room.getRoomId());
    }

    private void checkExecutionThread() {
        if (BuildConfig.DEBUG && Thread.currentThread() == executionThread) {
            throw new RuntimeException("Must be called in execution thread");
        }
    }

    public static Intent buildConnectIntent(final Room room) {
        return new Intent(ACTION_CONNECT).putExtra(ACTION_EXTRA_ROOM, room);
    }

    public static Intent buildDisconnectIntent(final int roomId) {
        return new Intent(ACTION_DISCONNECT).putExtra(ACTION_EXTRA_ROOM_ID, roomId);
    }

    public void setCurrentRoomStatus(final @RoomStatus int newRoomStatus) {
        if (this.roomStatus != newRoomStatus) {
            this.roomStatus = newRoomStatus;
            broadcastManager.sendBroadcast(new Intent(ACTION_ROOM_STATUS_CHANGED)
                    .putExtra(ACTION_EXTRA_ROOM_ID, currRoom.getRoomId())
                    .putExtra(ACTION_EXTRA_ROOM_STATUS, newRoomStatus));
        }
    }

    private class LocalBinder extends Binder implements TalkBinder {

        @Override
        public int getCurrRoomStatus() {
            return roomStatus;
        }

        @Override
        public Integer getCurrRoomId() {
            return currRoom == null ? null : currRoom.getRoomId();
        }
    }

}
