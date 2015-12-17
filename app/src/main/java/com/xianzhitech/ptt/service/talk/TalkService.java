package com.xianzhitech.ptt.service.talk;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.SimpleArrayMap;

import com.xianzhitech.ptt.AppComponent;
import com.xianzhitech.ptt.engine.ITalkEngine;
import com.xianzhitech.ptt.engine.ITalkEngineFactory;
import com.xianzhitech.ptt.service.signal.Room;
import com.xianzhitech.ptt.util.Logger;
import com.xianzhitech.service.provider.SignalProvider;

import org.apache.commons.lang3.StringUtils;

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
    public static final String ACTION_SET_FOCUS = "action_request_focus";

    public static final String ACTION_EXTRA_GROUP_ID = "extra_group_id";
    public static final String ACTION_EXTRA_HAS_AUDIO_FOCUS = "action_extra_has_audio_focus";

    private final IBinder binder = new LocalBinder();
    @RoomStatus int roomStatus = TalkBinder.ROOM_STATUS_NOT_CONNECTED;

    @Nullable String currGroupId;
    @Nullable Room currRoom;

    private final SimpleArrayMap<String, ITalkEngine> talkEngineMap = new SimpleArrayMap<>();
    private SignalProvider signalProvider;
    private ITalkEngineFactory talkEngineFactory;
    private LocalBroadcastManager broadcastManager;
    private AudioManager audioManager;

    @Override
    public void onCreate() {
        super.onCreate();

        final AppComponent appComponent = (AppComponent) getApplication();

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        broadcastManager = LocalBroadcastManager.getInstance(this);
        signalProvider = appComponent.providesSignal();
        talkEngineFactory = appComponent.providesTalkEngineFactory();
    }

    @Override
    public void onDestroy() {
        for (int i = 0, count = talkEngineMap.size(); i < count; i++) {
            doDisconnect(talkEngineMap.keyAt(i), false);
        }

        broadcastManager = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(final Intent intent) {
        handleIntent(intent);
        return binder;
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (intent != null) {
            handleIntent(intent);
        }

        return START_STICKY;
    }

    private void handleIntent(final Intent intent) {
        final String action = intent.getAction();
        if (ACTION_CONNECT.equals(action)) {
            doConnect(intent.getStringExtra(ACTION_EXTRA_GROUP_ID));
        } else if (ACTION_DISCONNECT.equals(action)) {
            doDisconnect(intent.getStringExtra(ACTION_EXTRA_GROUP_ID), true);
        } else if (ACTION_SET_FOCUS.equals(action)) {
            doSetFocus(intent.getStringExtra(ACTION_EXTRA_GROUP_ID), intent.getBooleanExtra(ACTION_EXTRA_HAS_AUDIO_FOCUS, false));
        } else {
            throw new IllegalArgumentException("Unknown intent: " + intent);
        }
    }

    private void doSetFocus(final String groupId, final boolean hasFocus) {
        if (!StringUtils.equals(groupId, currGroupId)) {
            Logger.w(LOG_TAG, "Requesting focus %s for group %s, but current group is %s. Ignored.", hasFocus, groupId, currGroupId);
            return;
        }

        if (roomStatus == TalkBinder.ROOM_STATUS_ACTIVE && hasFocus) {
            Logger.w(LOG_TAG, "Group %s already has focus. Ignored.");
            return;
        }

        final ITalkEngine talkEngine;
        if ((roomStatus != TalkBinder.ROOM_STATUS_CONNECTED && (roomStatus != TalkBinder.ROOM_STATUS_ACTIVE)) ||
                currRoom == null || (talkEngine = (talkEngineMap.get(groupId))) == null) {
            Logger.e(LOG_TAG, "Group %s not connected yet", groupId);
            return;
        }

        if (hasFocus && signalProvider.requestFocus(currRoom.getRoomId())) {
            talkEngine.startSend();
            setCurrentRoomStatus(TalkBinder.ROOM_STATUS_ACTIVE);
        }
        else if (!hasFocus && (roomStatus == TalkBinder.ROOM_STATUS_ACTIVE)) {
            setCurrentRoomStatus(TalkBinder.ROOM_STATUS_CONNECTED);
            talkEngine.stopSend();
            signalProvider.releaseFocus(currRoom.getRoomId());
        }
    }

    private void doDisconnect(final String groupId, final boolean stopIfNoConnectionLeft) {
        try {
            final ITalkEngine talkEngine = talkEngineMap.remove(groupId);
            if (talkEngine == null) {
                Logger.w(LOG_TAG, "Room %s already disconnected", groupId);
                return;
            }

            final boolean isCurrentRoom = StringUtils.equals(this.currGroupId, groupId);

            if (isCurrentRoom) {
                setCurrentRoomStatus(TalkBinder.ROOM_STATUS_DISCONNECTING);
            }

            Logger.d(LOG_TAG, "Disconnecting from room %s", groupId);

            if (isCurrentRoom) {
                audioManager.abandonAudioFocus(null);
                audioManager.setMode(AudioManager.MODE_NORMAL);
                setCurrentRoomStatus(TalkBinder.ROOM_STATUS_NOT_CONNECTED);
                this.currGroupId = null;
            }

            talkEngine.dispose();
            signalProvider.quitRoom(groupId);
        }
        finally {
            if (stopIfNoConnectionLeft && talkEngineMap.size() == 0) {
                stopSelf();
            }
        }

    }

    private void doConnect(final @NonNull String groupId) {
        if (StringUtils.equals(currGroupId, groupId)) {
            Logger.d(LOG_TAG, "Group %s already connected", groupId);
            return;
        }

        if (currGroupId != null) {
            doDisconnect(currGroupId, true);
        }

        Logger.d(LOG_TAG, "Connecting to room %s", groupId);

        currGroupId = groupId;
        setCurrentRoomStatus(TalkBinder.ROOM_STATUS_CONNECTING);

        final ITalkEngine engine = talkEngineFactory.createEngine(this);
        currRoom = signalProvider.joinRoom(groupId);
        talkEngineMap.put(groupId, engine);
        engine.connect(currRoom);

        setCurrentRoomStatus(TalkBinder.ROOM_STATUS_CONNECTED);
    }

    public static Intent buildConnectIntent(final String groupId) {
        return new Intent(ACTION_CONNECT).putExtra(ACTION_EXTRA_GROUP_ID, groupId);
    }

    public static Intent buildDisconnectIntent(final String groupId) {
        return new Intent(ACTION_DISCONNECT).putExtra(ACTION_EXTRA_GROUP_ID, groupId);
    }

    public static Intent buildSetAudioFocusIntent(final String groupId, final boolean hasAudioFocus) {
        return new Intent(ACTION_SET_FOCUS)
                .putExtra(ACTION_EXTRA_GROUP_ID, groupId)
                .putExtra(ACTION_EXTRA_HAS_AUDIO_FOCUS, hasAudioFocus);
    }

    public void setCurrentRoomStatus(final @RoomStatus int newRoomStatus) {
        if (this.roomStatus != newRoomStatus) {
            this.roomStatus = newRoomStatus;
            broadcastManager.sendBroadcast(new Intent(ACTION_ROOM_STATUS_CHANGED)
                    .putExtra(ACTION_EXTRA_GROUP_ID, currGroupId)
                    .putExtra(ACTION_EXTRA_ROOM_STATUS, newRoomStatus));
        }
    }

    private class LocalBinder extends Binder implements TalkBinder {

        @Override
        public int getCurrRoomStatus() {
            return roomStatus;
        }

        @Override
        public String getCurrGroupId() {
            return currGroupId;
        }
    }

}
