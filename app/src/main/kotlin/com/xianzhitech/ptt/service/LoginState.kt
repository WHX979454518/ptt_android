package com.xianzhitech.ptt.service

data class LoginState(val status: LoginStatus,
                      val currentUserID: String?) {
    companion object {
        @JvmStatic val EMPTY = LoginState(LoginStatus.IDLE, null)
    }
}