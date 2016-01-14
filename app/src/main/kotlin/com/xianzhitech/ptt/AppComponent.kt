package com.xianzhitech.ptt

import com.squareup.okhttp.OkHttpClient
import com.xianzhitech.ptt.engine.BtEngine
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.service.BackgroundServiceBinder
import com.xianzhitech.ptt.service.provider.PreferenceStorageProvider
import rx.Observable

/**

 * 应用程序组件. 提供各种库依赖

 * Created by fanchao on 13/12/15.
 */
interface AppComponent {
    val httpClient: OkHttpClient
    val talkEngineProvider: TalkEngineProvider
    val preferenceProvider: PreferenceStorageProvider

    // Configuration
    val signalServerEndpoint: String

    val btEngine : BtEngine

    // Repositories
    val userRepository: UserRepository
    val groupRepository: GroupRepository
    val roomRepository: RoomRepository
    val contactRepository: ContactRepository

    // Service
    fun connectToBackgroundService(): Observable<BackgroundServiceBinder>
}
