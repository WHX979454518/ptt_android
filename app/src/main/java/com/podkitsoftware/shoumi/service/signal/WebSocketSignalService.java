package com.podkitsoftware.shoumi.service.signal;

import com.podkitsoftware.shoumi.model.Room;

/**
 *
 * 基于WebSocket的信号服务器
 *
 * Created by fanchao on 13/12/15.
 */
public class WebSocketSignalService implements SignalService {
    private final String endpointUrl;
    private final int endpointPort;

    public WebSocketSignalService(final String endpointUrl, final int endpointPort) {
        this.endpointUrl = endpointUrl;
        this.endpointPort = endpointPort;
    }

    @Override
    public Room joinRoom(final String groupId) {
        return null;
    }

    @Override
    public void quitRoom(final String groupId) {
    }

    @Override
    public boolean requestFocus(final int roomId) {
        return false;
    }

    @Override
    public void releaseFocus(final int roomId) {

    }
}
