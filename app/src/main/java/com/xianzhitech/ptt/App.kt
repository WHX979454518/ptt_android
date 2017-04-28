package com.xianzhitech.ptt

import com.xianzhitech.ptt.app.BuildConfig


open class App : BaseApp() {
    override val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    override val appServerEndpoint: String
        get() = BuildConfig.APP_SERVER_ENDPOINT

}