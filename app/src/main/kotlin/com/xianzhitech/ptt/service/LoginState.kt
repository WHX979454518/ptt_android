package com.xianzhitech.ptt.service

data class LoginState(val status : LoginStatus = LoginStatus.IDLE,
                      val currentUserID: String? = null) {
}