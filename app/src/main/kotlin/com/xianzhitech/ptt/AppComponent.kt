package com.xianzhitech.ptt

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.xianzhitech.ptt.api.AppApi
import com.xianzhitech.ptt.api.SignalApi
import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.data.Storage
import com.xianzhitech.ptt.media.MediaButtonHandler
import com.xianzhitech.ptt.repo.*
import com.xianzhitech.ptt.service.AppService
import com.xianzhitech.ptt.service.handler.SignalServiceHandler
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
    val gson: Gson

    val storage : Storage
    val objectMapper : ObjectMapper

    val appApi : AppApi
    val signalBroker : SignalBroker

    val appServerEndpoint : String
    val appService: AppService
    val signalHandler: SignalServiceHandler

    val mediaButtonHandler: MediaButtonHandler

    // Repositories
    val userRepository: UserRepository
    val groupRepository: GroupRepository
    val roomRepository: RoomRepository
    val contactRepository: ContactRepository
    val messageRepository : MessageRepository

    val statisticCollector: StatisticCollector
    val activityProvider: ActivityProvider
}
