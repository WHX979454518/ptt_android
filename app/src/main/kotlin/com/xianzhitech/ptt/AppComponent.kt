package com.xianzhitech.ptt

import com.xianzhitech.ptt.engine.BtEngine
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.service.SignalService
import com.xianzhitech.ptt.update.UpdateManager
import okhttp3.OkHttpClient

/**

 * 应用程序组件. 提供各种库依赖

 * Created by fanchao on 13/12/15.
 */
interface AppComponent {
    val httpClient: OkHttpClient
    val talkEngineProvider: TalkEngineProvider
    val preference: Preference
    val updateManager : UpdateManager

    // Configuration
    val signalServerEndpoint: String

    val btEngine : BtEngine

    // Repositories
    val userRepository: UserRepository
    val groupRepository: GroupRepository
    val roomRepository: RoomRepository
    val contactRepository: ContactRepository

    // Service
    val signalService: SignalService
}
