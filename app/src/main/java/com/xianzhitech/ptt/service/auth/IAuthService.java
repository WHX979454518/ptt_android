package com.xianzhitech.ptt.service.auth;

import rx.Observable;

/**
 * 用户鉴权接口
 *
 * Created by fanchao on 14/12/15.
 */
public interface IAuthService {
    Observable<Person> login(String username, String password);
    void logout();
}
