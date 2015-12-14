package com.podkitsoftware.shoumi.service.signal;

import com.podkitsoftware.shoumi.model.Room;

public interface ISignalService {
    Room joinRoom(String groupId);
    void quitRoom(String groupId);
    boolean requestFocus(int roomId);
    void releaseFocus(int roomId);
}
