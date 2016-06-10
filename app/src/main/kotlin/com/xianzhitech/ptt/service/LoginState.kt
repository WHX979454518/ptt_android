package com.xianzhitech.ptt.service

data class LoginState(val status : LoginStatus,
                      val currentUser: String?) {
    companion object {
        @JvmStatic val EMPTY = LoginState(LoginStatus.IDLE, null)
    }
}