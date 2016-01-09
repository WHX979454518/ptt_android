package com.xianzhitech.ptt.presenter.base

import java.util.*

/**
 * 表现层
 *
 * Created by fanchao on 9/01/16.
 */
public interface Presenter<T : PresenterView> {
    fun attachView(view: T)
    fun detachView(view: T)
}

public abstract class BasePresenter<T : PresenterView> : Presenter<T> {
    protected val views = ArrayList<T>(1)

    override fun attachView(view: T) {
        views.add(view)
    }

    override fun detachView(view: T) {
        views.remove(view)
    }
}