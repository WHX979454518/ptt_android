package com.xianzhitech.ptt.service.room

import android.os.Handler
import android.os.Looper
import android.test.ServiceTestCase
import com.xianzhitech.ptt.App
import com.xianzhitech.ptt.ext.toBlockingFirst
import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.service.provider.CreateGroupConversationRequest
import com.xianzhitech.ptt.service.talk.RoomStatus
import rx.observers.TestSubscriber
import java.util.concurrent.TimeUnit

/**
 * Created by fanchao on 18/12/15.
 */

class RoomServiceTest : ServiceTestCase<RoomService>(RoomService::class.java) {

    private lateinit var logonUser: Person
    private lateinit var conversation: Conversation

    override fun setUp() {
        super.setUp()

        val authProvider = MockAuthProvider()
        logonUser = authProvider.login(MockPersons.PERSON_1.name, "123").toBlockingFirst()
        val signalProvider = MockSignalProvider(logonUser, MockPersons.ALL, MockGroups.ALL, MockGroups.GROUP_MEMBERS)
        val talkEngineProvider = MockTalkEngineProvider()

        conversation = signalProvider.createConversation(listOf(CreateGroupConversationRequest(MockGroups.GROUP_1.id))).toBlockingFirst()

        application = object : App() {
            override fun providesAuth() = authProvider
            override fun providesSignal() = signalProvider
            override fun providesTalkEngine() = talkEngineProvider
        }
    }

    private fun runOnMainThread(runnable: () -> Unit) {
        Handler(Looper.getMainLooper()).post(runnable)
    }

    fun testConnect() {
        var statusIterator = RoomService.getRoomStatus(context).timeout(1, TimeUnit.SECONDS).toBlocking().iterator
        runOnMainThread({ startService(RoomService.buildConnect(context, conversation.id)) })
        assertEquals(listOf(RoomStatus.CONNECTING, RoomStatus.CONNECTED), listOf(statusIterator.next(), statusIterator.next()))
    }

    fun testDisconnect() {
        startService(RoomService.buildConnect(context, conversation.id))
        startService(RoomService.buildDisconnect(context))

        val testSubscriber = TestSubscriber<Int>()
        RoomService.getRoomStatus(context).subscribe(testSubscriber)
        testSubscriber.assertNoErrors()
        testSubscriber.assertValue(RoomStatus.NOT_CONNECTED)
    }
}