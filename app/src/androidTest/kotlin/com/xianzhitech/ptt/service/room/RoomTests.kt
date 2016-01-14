package com.xianzhitech.ptt.service.room

import android.test.ServiceTestCase
import com.xianzhitech.ptt.App
import com.xianzhitech.ptt.ext.toBlockingFirst
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.service.provider.JoinRoomFromGroup
import rx.android.plugins.RxAndroidPlugins
import rx.android.plugins.RxAndroidSchedulersHook
import rx.schedulers.Schedulers
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.firstOrNull

/**
 * Created by fanchao on 18/12/15.
 */

class RoomServiceTest : ServiceTestCase<RoomService>(RoomService::class.java) {

    companion object {
        var rxInitialized = AtomicBoolean(false)
    }

    private lateinit var logonUser: User
    private lateinit var room: Room
    private lateinit var mockTalkEngineProvider: MockTalkEngineProvider
    private lateinit var mockSignalProvider: MockSignalProvider
    private lateinit var mockAuthProvider: MockAuthProvider

    override fun setUp() {
        super.setUp()

        mockAuthProvider = MockAuthProvider()
        logonUser = mockAuthProvider.login(MockPersons.PERSON_1.name, "123").toBlockingFirst().person
        mockSignalProvider = MockSignalProvider(logonUser, MockPersons.ALL, MockGroups.ALL, MockGroups.GROUP_MEMBERS)
        mockTalkEngineProvider = MockTalkEngineProvider()

        room = mockSignalProvider.createRoom(JoinRoomFromGroup(MockGroups.GROUP_1.id)).toBlockingFirst()

        if (rxInitialized.compareAndSet(false, true)) {
            RxAndroidPlugins.getInstance().registerSchedulersHook(object : RxAndroidSchedulersHook() {
                override fun getMainThreadScheduler() = Schedulers.immediate()
            })
        }

        application = object : App() {
            override val signalProvider = mockSignalProvider
            override val authProvider = mockAuthProvider
            override val talkEngineProvider = mockTalkEngineProvider
        }
    }

    fun testConnect() {
        val conn = bindService(RoomService.buildConnect(context, room.id)) as RoomServiceBinder
        assertEquals(RoomStatus.CONNECTED, conn.roomStatus)
        assertEquals(mockSignalProvider.conversations.entries.firstOrNull()?.value?.room?.id, mockTalkEngineProvider.createdEngines[0].connectedRoomInfo?.id)
    }

    fun testDisconnect() {
        val conn = bindService(RoomService.buildConnect(context, room.id)) as RoomServiceBinder
        startService(RoomService.buildDisconnect(context))

        assertEquals(RoomStatus.NOT_CONNECTED, conn.roomStatus)
        assertEquals(true, mockTalkEngineProvider.createdEngines[0].disposed)
    }

    fun testFocus() {
        val conn = bindService(RoomService.buildConnect(context, room.id)) as RoomServiceBinder
        startService(RoomService.buildRequestFocus(context, true))

        assertEquals(RoomStatus.ACTIVE, conn.roomStatus)
        assertEquals(true, mockTalkEngineProvider.createdEngines[0].sending)
        assertEquals(mockAuthProvider.logonUser?.id, mockSignalProvider.peekCurrentSpeakerId(room.id))

        startService(RoomService.buildRequestFocus(context, false))
        assertEquals(false, mockTalkEngineProvider.createdEngines[0].sending)
        assertEquals(RoomStatus.CONNECTED, conn.roomStatus)
    }

    fun testRequestFocusFail() {
        mockSignalProvider.ensureCurrentSpeakerSubject(room.id).onNext(MockPersons.PERSON_5.id)
        val conn = bindService(RoomService.buildConnect(context, room.id)) as RoomServiceBinder
        assertEquals(MockPersons.PERSON_5.id, mockSignalProvider.peekCurrentSpeakerId(room.id))

        startService(RoomService.buildRequestFocus(context, true))
        assertEquals(MockPersons.PERSON_5.id, mockSignalProvider.peekCurrentSpeakerId(room.id))
        assertNotSame(RoomStatus.ACTIVE, conn.roomStatus)
    }
}