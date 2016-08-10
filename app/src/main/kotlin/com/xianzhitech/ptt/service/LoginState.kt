package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.model.User

data class LoginState(val status: LoginStatus,
                      val currentUser : User?) {
    companion object {
        @JvmStatic val EMPTY = LoginState(LoginStatus.IDLE, null)
    }
}

val LoginState.currentUserID : String?
get() = currentUser?.id