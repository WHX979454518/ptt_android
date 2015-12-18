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
import com.xianzhitech.ptt.engine.TalkEngine;
import com.xianzhitech.ptt.engine.TalkEngineProvider;
import com.xianzhitech.ptt.model.Room;
import com.xianzhitech.ptt.service.provider.SignalProvider;
import com.xianzhitech.ptt.util.Logger;
import hugo.weaving.DebugLog;
import org.apache.commons.lang3.StringUtils;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

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

    public static final String ACTION_EXTRA_CONVERSATION_ID = "extra_conv_id";
    public static final String ACTION_EXTRA_HAS_AUDIO_FOCUS = "action_extra_has_audio_focus";

    private final IBinder binder = new LocalServiceBinder();
    @RoomStatus int roomStatus = TalkServiceBinder.ROOM_STATUS_NOT_CONNECTED;

    @Nullable String currConversationId;
    @Nullable
    Room currRoom;

    private final SimpleArrayMap<String, TalkEngine> talkEngineMap = new SimpleArrayMap<>();
    private SignalProvider signalProvider;
    private TalkEngineProvider talkEngineProvider;
    private LocalBroadcastManager broadcastManager;
    private AudioManager audioManager;

    private Subscription requestFocusSubscription;
    private Subscription connectSubscription;

    @Override
    public void onCreate() {
        super.onCreate();

        final AppComponent appComponent = (AppComponent) getApplication();

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        broadcastManager = LocalBroadcastManager.getInstance(this);
        signalProvider = appComponent.providesSignal();
        talkEngineProvider = appComponent.providesTalkEngine();
    }

    @Override
    public void onDestroy() {
        for (int i = 0, count = talkEngineMap.size(); i < count; i++) {
            doDisconnect(talkEngineMap.keyAt(i), false);
        }

        broadcastManager = null;
        cancelRequestFocus();
        cancelConnect();
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

    @DebugLog
    private void handleIntent(final Intent intent) {
        final String action = intent.getAction();
        if (ACTION_CONNECT.equals(action)) {
            doConnect(intent.getStringExtra(ACTION_EXTRA_CONVERSATION_ID));
        } else if (ACTION_DISCONNECT.equals(action)) {
            doDisconnect(intent.getStringExtra(ACTION_EXTRA_CONVERSATION_ID), true);
        } else if (ACTION_SET_FOCUS.equals(action)) {
            doSetFocus(intent.getStringExtra(ACTION_EXTRA_CONVERSATION_ID), intent.getBooleanExtra(ACTION_EXTRA_HAS_AUDIO_FOCUS, false));
        } else {
            throw new IllegalArgumentException("Unknown intent: " + intent);
        }
    }

    @DebugLog
    private void doSetFocus(final String groupId, final boolean hasFocus) {
        if (!StringUtils.equals(groupId, currConversationId)) {
            Logger.w(LOG_TAG, "Requesting focus %s for group %s, but current group is %s. Ignored.", hasFocus, groupId, currConversationId);
            return;
        }

        if (roomStatus == TalkServiceBinder.ROOM_STATUS_ACTIVE && hasFocus) {
            Logger.w(LOG_TAG, "Group %s already has focus. Ignored.");
            return;
        }

        final TalkEngine talkEngine;
        if ((roomStatus != TalkServiceBinder.ROOM_STATUS_CONNECTED && (roomStatus != TalkServiceBinder.ROOM_STATUS_ACTIVE)) ||
                currRoom == null || (talkEngine = (talkEngineMap.get(groupId))) == null) {
            Logger.e(LOG_TAG, "Group %s not connected yet", groupId);
            return;
        }

        cancelRequestFocus();

        if (hasFocus) {
            requestFocusSubscription = signalProvider.requestFocus(currRoom.getId())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new PrivateSubscriber<Boolean>() {
                        @Override
                        public void onNext(final Boolean success) {
                            if (success) {
                                talkEngine.startSend();
                                setCurrentRoomStatus(TalkServiceBinder.ROOM_STATUS_ACTIVE);
                            }
                        }
                    });
        }
        else if (roomStatus == TalkServiceBinder.ROOM_STATUS_ACTIVE) {
            setCurrentRoomStatus(TalkServiceBinder.ROOM_STATUS_CONNECTED);
            talkEngine.stopSend();
            signalProvider.releaseFocus(currRoom.getId())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new PrivateSubscriber<>());
        }
    }

    @DebugLog
    private void cancelRequestFocus() {
        if (requestFocusSubscription != null) {
            requestFocusSubscription.unsubscribe();
            requestFocusSubscription = null;
        }
    }

    @DebugLog
    private void doDisconnect(final String groupId, final boolean stopIfNoConnectionLeft) {
        try {
            final TalkEngine talkEngine = talkEngineMap.remove(groupId);
            if (talkEngine == null) {
                Logger.w(LOG_TAG, "Room %s already disconnected", groupId);
                return;
            }

            final boolean isCurrentRoom = StringUtils.equals(this.currConversationId, groupId);

            if (isCurrentRoom) {
                setCurrentRoomStatus(TalkServiceBinder.ROOM_STATUS_DISCONNECTING);
            }

            Logger.d(LOG_TAG, "Disconnecting from room %s", groupId);

            if (isCurrentRoom) {
                audioManager.abandonAudioFocus(null);
                audioManager.setMode(AudioManager.MODE_NORMAL);
                setCurrentRoomStatus(TalkServiceBinder.ROOM_STATUS_NOT_CONNECTED);
                this.currConversationId = null;
            }

            talkEngine.dispose();
            signalProvider.quitConversation(groupId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new PrivateSubscriber<>());
        }
        finally {
            if (stopIfNoConnectionLeft && talkEngineMap.size() == 0) {
                stopSelf();
            }
        }
    }

    @DebugLog
    private void doConnect(final @NonNull String conversationId) {
        if (StringUtils.equals(currConversationId, conversationId)) {
            Logger.d(LOG_TAG, "Group %s already connected", conversationId);
            return;
        }

        if (currConversationId != null) {
            doDisconnect(currConversationId, true);
        }

        Logger.d(LOG_TAG, "Connecting to room %s", conversationId);

        currConversationId = conversationId;
        setCurrentRoomStatus(TalkServiceBinder.ROOM_STATUS_CONNECTING);

        cancelConnect();

        connectSubscription = signalProvider.joinConversation(conversationId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new PrivateSubscriber<Room>() {
                    @Override
                    public void onNext(final Room room) {
                        final TalkEngine engine = talkEngineProvider.createEngine(TalkService.this);
                        currRoom = room;
                        talkEngineMap.put(conversationId, engine);
                        engine.connect(currRoom);

                        setCurrentRoomStatus(TalkServiceBinder.ROOM_STATUS_CONNECTED);
                    }
                });
    }

    private void cancelConnect() {
        if (connectSubscription != null) {
            connectSubscription.unsubscribe();
            connectSubscription = null;
        }
    }

    public static Intent buildConnectIntent(final String groupId) {
        return new Intent(ACTION_CONNECT).putExtra(ACTION_EXTRA_CONVERSATION_ID, groupId);
    }

    public static Intent buildDisconnectIntent(final String groupId) {
        return new Intent(ACTION_DISCONNECT).putExtra(ACTION_EXTRA_CONVERSATION_ID, groupId);
    }

    public static Intent buildSetAudioFocusIntent(final String groupId, final boolean hasAudioFocus) {
        return new Intent(ACTION_SET_FOCUS)
                .putExtra(ACTION_EXTRA_CONVERSATION_ID, groupId)
                .putExtra(ACTION_EXTRA_HAS_AUDIO_FOCUS, hasAudioFocus);
    }

    public void setCurrentRoomStatus(final @RoomStatus int newRoomStatus) {
        if (this.roomStatus != newRoomStatus) {
            this.roomStatus = newRoomStatus;
            broadcastManager.sendBroadcast(new Intent(ACTION_ROOM_STATUS_CHANGED)
                    .putExtra(ACTION_EXTRA_CONVERSATION_ID, currConversationId)
                    .putExtra(ACTION_EXTRA_ROOM_STATUS, newRoomStatus));
        }
    }

    private class PrivateSubscriber<T> extends Subscriber<T> {

        @Override
        public void onCompleted() {}

        @Override
        public void onError(final Throwable e) {}

        @Override
        public void onNext(final T t) {}
    }

    private class LocalServiceBinder extends Binder implements TalkServiceBinder {

        @Override
        public int getCurrRoomStatus() {
            return roomStatus;
        }

        @Override
        public String getCurrGroupId() {
            return currConversationId;
        }
    }

}
