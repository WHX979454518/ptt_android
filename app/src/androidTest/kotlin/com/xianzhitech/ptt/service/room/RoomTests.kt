package com.xianzhitech.ptt.service.room

import android.test.ServiceTestCase
import com.xianzhitech.ptt.App
import com.xianzhitech.ptt.ext.toBlockingFirst
import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.service.provider.CreateGroupConversationRequest
import com.xianzhitech.ptt.service.talk.RoomStatus
import rx.android.plugins.RxAndroidPlugins
import rx.android.plugins.RxAndroidSchedulersHook
import rx.schedulers.Schedulers
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by fanchao on 18/12/15.
 */

class RoomServiceTest : ServiceTestCase<RoomService>(RoomService::class.java) {

    companion object {
        var rxInitialized = AtomicBoolean(false)
    }

    private lateinit var logonUser: Person
    private lateinit var conversation: Conversation
    private lateinit var talkEngineProvider: MockTalkEngineProvider
    private lateinit var signalProvider: MockSignalProvider

    override fun setUp() {
        super.setUp()

        val authProvider = MockAuthProvider()
        logonUser = authProvider.login(MockPersons.PERSON_1.name, "123").toBlockingFirst()
        signalProvider = MockSignalProvider(logonUser, MockPersons.ALL, MockGroups.ALL, MockGroups.GROUP_MEMBERS)
        talkEngineProvider = MockTalkEngineProvider()

        conversation = signalProvider.createConversation(listOf(CreateGroupConversationRequest(MockGroups.GROUP_1.id))).toBlockingFirst()

        if (rxInitialized.compareAndSet(false, true)) {
            RxAndroidPlugins.getInstance().registerSchedulersHook(object : RxAndroidSchedulersHook() {
                override fun getMainThreadScheduler() = Schedulers.immediate()
            })
        }

        application = object : App() {
            override fun providesAuth() = authProvider
            override fun providesSignal() = signalProvider
            override fun providesTalkEngine() = talkEngineProvider
        }
    }

    fun testConnect() {
        val conn = bindService(RoomService.buildConnect(context, conversation.id)) as RoomServiceBinder
        assertEquals(RoomStatus.CONNECTED, conn.roomStatus)
        assertEquals(signalProvider.conversations.entries.firstOrNull()?.value?.conversation?.id, talkEngineProvider.createdEngines[0].connectedRoom?.id)
    }

    fun testDisconnect() {
        val conn = bindService(RoomService.buildConnect(context, conversation.id)) as RoomServiceBinder
        startService(RoomService.buildDisconnect(context))

        assertEquals(RoomStatus.NOT_CONNECTED, conn.roomStatus)
        assertEquals(true, talkEngineProvider.createdEngines[0].disposed)
    }

    fun testFocus() {
        val conn = bindService(RoomService.buildConnect(context, conversation.id)) as RoomServiceBinder
        startService(RoomService.buildRequestFocus(context, true))

        assertEquals(RoomStatus.ACTIVE, conn.roomStatus)
        assertEquals(true, talkEngineProvider.createdEngines[0].sending)

        startService(RoomService.buildRequestFocus(context, false))
        assertEquals(false, talkEngineProvider.createdEngines[0].sending)
        assertEquals(RoomStatus.CONNECTED, conn.roomStatus)
    }
}