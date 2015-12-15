package com.podkitsoftware.shoumi.service.auth;

import com.podkitsoftware.shoumi.model.Person;

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
