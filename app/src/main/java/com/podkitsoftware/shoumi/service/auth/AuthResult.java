package com.podkitsoftware.shoumi.service.auth;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

/**
 *
 * 用户登陆结果
 *
 * Created by fanchao on 14/12/15.
 */
@JsonObject
public class AuthResult {

    @JsonField(name = "user_id")
    String userId;

    @JsonField(name = "token")
    String token;

    public AuthResult() {
    }

    public AuthResult(final String userId, final String token) {
        this.userId = userId;
        this.token = token;
    }

    public String getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }
}
