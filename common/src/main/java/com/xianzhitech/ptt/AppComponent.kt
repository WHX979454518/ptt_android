package com.xianzhitech.ptt

import com.fasterxml.jackson.databind.ObjectMapper
import com.xianzhitech.ptt.api.AppApi
import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.data.Storage
import com.xianzhitech.ptt.media.MediaButtonHandler
import com.xianzhitech.ptt.service.handler.StatisticCollector
import com.xianzhitech.ptt.ui.ActivityProvider
import okhttp3.OkHttpClient

/**

 * 应用程序组件. 提供各种库依赖

 * Created by fanchao on 13/12/15.
 */
interface AppComponent {
    val httpClient: OkHttpClient
    val preference: Preference

    val storage : Storage
    val objectMapper : ObjectMapper

    val appApi : AppApi
    val signalBroker : SignalBroker

    val appServerEndpoint : String

    val mediaButtonHandler: MediaButtonHandler

    val statisticCollector: StatisticCollector
    val activityProvider: ActivityProvider

    val currentVersion : String
}
