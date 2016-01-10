package com.xianzhitech.ptt.presenter.base

public interface PresenterView {
    fun showLoading(visible: Boolean)
    fun showError(err: Throwable)
}