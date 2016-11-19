package com.xianzhitech.ptt

import com.xianzhitech.ptt.media.AudioHandler
import com.xianzhitech.ptt.media.MediaButtonHandler
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.UserRepository
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

    val audioHandler : AudioHandler
    val appServerEndpoint : String
    val appService: AppService
    val signalHandler: SignalServiceHandler

    val mediaButtonHandler: MediaButtonHandler

    // Repositories
    val userRepository: UserRepository
    val groupRepository: GroupRepository
    val roomRepository: RoomRepository
    val contactRepository: ContactRepository

    val statisticCollector: StatisticCollector
    val activityProvider: ActivityProvider
}
