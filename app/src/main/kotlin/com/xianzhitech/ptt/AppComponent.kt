package com.xianzhitech.ptt

import com.google.gson.Gson
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
