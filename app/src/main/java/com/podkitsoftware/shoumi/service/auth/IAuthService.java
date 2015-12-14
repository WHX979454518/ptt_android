package com.podkitsoftware.shoumi.service.auth;

/**
 * 用户鉴权接口
 *
 * Created by fanchao on 14/12/15.
 */
public interface IAuthService {
    AuthResult login(String username, char[] password);
    void logout(String token);
}
