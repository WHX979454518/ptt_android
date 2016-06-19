package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.model.User

data class LoginState(val status: LoginStatus,
                      val currentUserID: String?,
                      val currentUser : User?) {
    companion object {
        @JvmStatic val EMPTY = LoginState(LoginStatus.IDLE, null, null)
    }
}