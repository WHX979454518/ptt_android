package com.xianzhitech.ptt.mock;

import com.xianzhitech.ptt.service.signal.Room;
import com.xianzhitech.service.provider.SignalProvider;

import java.util.Map;

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
    public Room joinRoom(final String groupId) {
        final RoomInfo info = rooms.get(groupId);
        info.joined = true;
        return info.room;
    }

    @Override
    public void quitRoom(final String groupId) {
        rooms.get(groupId).joined = false;
    }

    @Override
    public boolean requestFocus(final int roomId) {
        final RoomInfo roomInfo = findRoomById(roomId);
        if (roomInfo.canHasFocus) {
            roomInfo.hasFocus = true;
            return true;
        }

        return false;
    }

    @Override
    public void releaseFocus(final int roomId) {
        findRoomById(roomId).hasFocus = false;
    }
}
