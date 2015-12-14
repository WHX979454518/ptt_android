package com.podkitsoftware.shoumi.mock;

import com.podkitsoftware.shoumi.model.Room;
import com.podkitsoftware.shoumi.service.signal.SignalService;

import java.util.Map;

import rx.subjects.PublishSubject;

/**
 * Created by fanchao on 13/12/15.
 */
public class MockSignalService implements SignalService {
    public MockSignalService(final Map<String, RoomInfo> rooms) {
        this.rooms = rooms;
    }

    public static class RoomInfo {
        public final Room room;
        public final boolean canHasFocus;
        public final PublishSubject<Boolean> hasFocus = PublishSubject.create();

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
    public Room getRoom(final String groupId) {
        return rooms.get(groupId).room;
    }

    @Override
    public boolean requestFocus(final int roomId) {
        final RoomInfo roomInfo = findRoomById(roomId);
        if (roomInfo.canHasFocus) {
            roomInfo.hasFocus.onNext(true);
            return true;
        }

        return false;
    }

    @Override
    public void releaseFocus(final int roomId) {
        findRoomById(roomId).hasFocus.onNext(false);
    }
}
