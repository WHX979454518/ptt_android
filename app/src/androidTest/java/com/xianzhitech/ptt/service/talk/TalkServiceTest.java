package com.xianzhitech.ptt.service.talk;

import android.test.ServiceTestCase;

import com.google.common.collect.ImmutableMap;
import com.xianzhitech.ptt.App;
import com.xianzhitech.ptt.engine.TalkEngineFactory;
import com.xianzhitech.ptt.mock.MockSignalProvider;
import com.xianzhitech.ptt.mock.MockTalkEngineFactory;
import com.xianzhitech.ptt.service.signal.Room;
import com.xianzhitech.service.provider.SignalProvider;

import java.util.Map;

/**
 * Created by fanchao on 13/12/15.
 */
public class TalkServiceTest extends ServiceTestCase<TalkService> {

    private static final String GROUP_ID_1 = "GroupId_1";
    private static final String GROUP_ID_2 = "GroupId_2";
    private static final String GROUP_ID_3 = "GroupId_3";

    private MockSignalProvider mockSignalService;
    private MockTalkEngineFactory mockTalkEngineFactory;
    private Map<String, MockSignalProvider.RoomInfo> rooms;

    public TalkServiceTest() {
        super(TalkService.class);
    }

    public void setUp() throws Exception {
        super.setUp();

        rooms = ImmutableMap.of(
                GROUP_ID_1, new MockSignalProvider.RoomInfo(new Room(1, 2, "localhost", 8000), true),
                GROUP_ID_2, new MockSignalProvider.RoomInfo(new Room(2, 2, "localhost", 8000), true),
                GROUP_ID_3, new MockSignalProvider.RoomInfo(new Room(3, 2, "localhost", 8000), false)
        );
        mockSignalService = new MockSignalProvider(rooms);
        mockTalkEngineFactory = new MockTalkEngineFactory();

        setApplication(new App() {
            @Override
            public SignalProvider providesSignal() {
                return mockSignalService;
            }

            @Override
            public TalkEngineFactory providesTalkEngineFactory() {
                return mockTalkEngineFactory;
            }
        });
    }

    public void testJoinRoom() {
        final TalkServiceBinder binder = (TalkServiceBinder) bindService(TalkService.buildConnectIntent(GROUP_ID_1));
        assertEquals(TalkServiceBinder.ROOM_STATUS_CONNECTED, binder.getCurrRoomStatus());
        assertEquals(rooms.get(GROUP_ID_1).room, mockTalkEngineFactory.aliveEngines.get(0).room);
        assertTrue(mockSignalService.rooms.get(GROUP_ID_1).joined);
    }

    public void testQuitRoom() {
        final TalkServiceBinder binder = (TalkServiceBinder) bindService(TalkService.buildConnectIntent(GROUP_ID_1));
        startService(TalkService.buildDisconnectIntent(GROUP_ID_1));
        assertEquals(TalkServiceBinder.ROOM_STATUS_NOT_CONNECTED, binder.getCurrRoomStatus());
        assertTrue(mockTalkEngineFactory.aliveEngines.isEmpty());
        assertFalse(mockSignalService.rooms.get(GROUP_ID_1).joined);
    }

    public void testRequestFocus() {
        final TalkServiceBinder binder = (TalkServiceBinder) bindService(TalkService.buildConnectIntent(GROUP_ID_2));
        startService(TalkService.buildSetAudioFocusIntent(GROUP_ID_2, true));
        assertEquals(TalkServiceBinder.ROOM_STATUS_ACTIVE, binder.getCurrRoomStatus());
        assertTrue(mockSignalService.rooms.get(GROUP_ID_2).hasFocus);
        startService(TalkService.buildSetAudioFocusIntent(GROUP_ID_2, false));
        assertNotSame(TalkServiceBinder.ROOM_STATUS_ACTIVE, binder.getCurrRoomStatus());
        assertFalse(mockSignalService.rooms.get(GROUP_ID_2).hasFocus);
    }
}
