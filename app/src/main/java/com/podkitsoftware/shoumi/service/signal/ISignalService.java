package com.podkitsoftware.shoumi.service.signal;

public interface ISignalService {
    Room joinRoom(String groupId);
    void quitRoom(String groupId);
    boolean requestFocus(int roomId);
    void releaseFocus(int roomId);
}
