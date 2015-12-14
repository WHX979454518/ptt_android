package com.podkitsoftware.shoumi.service.signal;

import com.podkitsoftware.shoumi.service.auth.AuthResult;
import com.podkitsoftware.shoumi.service.auth.IAuthService;

/**
 *
 * 基于WebSocket的信号服务器
 *
 * Created by fanchao on 13/12/15.
 */
public class WebSocketSignalService implements ISignalService, IAuthService {
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

    @Override
    public AuthResult login(final String username, final char[] password) {
        return null;
    }

    @Override
    public void logout(final String token) {

    }
}
