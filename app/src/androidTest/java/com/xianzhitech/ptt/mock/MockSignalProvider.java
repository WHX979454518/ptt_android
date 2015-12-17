package com.xianzhitech.ptt.mock;

import com.xianzhitech.ptt.model.Conversation;
import com.xianzhitech.ptt.service.provider.CreateConversationRequest;
import com.xianzhitech.ptt.service.provider.SignalProvider;
import com.xianzhitech.ptt.service.signal.Room;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

import rx.Observable;

/**
 * Created by fanchao on 13/12/15.
 */
public class MockSignalProvider implements SignalProvider {
    public MockSignalProvider(final Map<String, RoomInfo> rooms) {
        this.rooms = rooms;
    }

    public static class RoomInfo {
        public final Room room;
        public final boolean canHasFocus;
        public boolean joined;
        public boolean hasFocus;

        public RoomInfo(final Room room, final boolean canHasFocus) {
            this.room = room;
            this.canHasFocus = canHasFocus;
        }
    }

    public final Map<String, RoomInfo> rooms;

    private RoomInfo findRoomById(final int roomId) {
        for (RoomInfo info : rooms.values()) {
            if (info.room.getRoomId() == roomId) {
                return info;
            }
        }

        return null;
    }

    @Override
    public Observable<Room> joinConversation(final String groupId) {
        final RoomInfo info = rooms.get(groupId);
        info.joined = true;
        return Observable.just(info.room);
    }

    @Override
    public Observable<Void> quitConversation(final String groupId) {
        rooms.get(groupId).joined = false;
        return Observable.just(null);
    }

    @Override
    public Observable<Boolean> requestFocus(final int roomId) {
        final RoomInfo roomInfo = findRoomById(roomId);
        if (roomInfo.canHasFocus) {
            roomInfo.hasFocus = true;
            return Observable.just(true);
        }

        return Observable.just(true);
    }

    @Override
    public Observable<Void> releaseFocus(final int roomId) {
        findRoomById(roomId).hasFocus = false;
        return Observable.just(null);
    }

    @NotNull @Override
    public Observable<Conversation> createConversation(@NotNull final Iterable<? extends CreateConversationRequest> requests) {
        //TODO:
        return Observable.empty();
    }

    @NotNull
    @Override
    public Observable<Void> deleteConversation(@NotNull final String conversationId) {
        //TODO:
        return Observable.empty();
    }
}
