package com.xianzhitech.ptt.presenter

import com.xianzhitech.ptt.presenter.base.PresenterView

interface LoginPresenterView : PresenterView {
    fun showLogin()
    fun showLoginSuccess()
}