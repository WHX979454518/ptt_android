package com.xianzhitech.ptt

import com.squareup.okhttp.OkHttpClient
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.presenter.ConversationListPresenter
import com.xianzhitech.ptt.presenter.LoginPresenter
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.ConversationRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.service.provider.AuthProvider
import com.xianzhitech.ptt.service.provider.PreferenceStorageProvider
import com.xianzhitech.ptt.service.provider.SignalProvider

/**

 * 应用程序组件. 提供各种库依赖

 * Created by fanchao on 13/12/15.
 */
interface AppComponent {
    val httpClient: OkHttpClient
    val signalProvider: SignalProvider
    val talkEngineProvider: TalkEngineProvider
    val authProvider: AuthProvider
    val preferenceProvider: PreferenceStorageProvider

    // Repositories
    val userRepository: UserRepository
    val groupRepository: GroupRepository
    val conversationRepository: ConversationRepository
    val contactRepository: ContactRepository

    // Presenters
    val loginPresenter: LoginPresenter
    val conversationListPresenter: ConversationListPresenter
}
