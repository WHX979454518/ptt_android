package com.podkitsoftware.shoumi.service.signal;

import com.podkitsoftware.shoumi.model.Room;

public interface SignalService {
    Room getRoom(String groupId);
    boolean requestFocus(int roomId);
    void releaseFocus(int roomId);
}
