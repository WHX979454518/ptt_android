package com.podkitsoftware.shoumi.service.talk;

import android.test.ServiceTestCase;

import com.google.common.collect.ImmutableMap;
import com.podkitsoftware.shoumi.App;
import com.podkitsoftware.shoumi.engine.TalkEngineFactory;
import com.podkitsoftware.shoumi.mock.MockSignalService;
import com.podkitsoftware.shoumi.mock.MockTalkEngineFactory;
import com.podkitsoftware.shoumi.model.Room;
import com.podkitsoftware.shoumi.service.signal.SignalService;

import java.util.Map;

/**
 * Created by fanchao on 13/12/15.
 */
public class TalkServiceTest extends ServiceTestCase<TalkService> {

    private static final String GROUP_ID_1 = "GroupId_1";
    private static final String GROUP_ID_2 = "GroupId_2";
    private static final String GROUP_ID_3 = "GroupId_3";

    private MockSignalService mockSignalService;
    private MockTalkEngineFactory mockTalkEngineFactory;
    private Map<String, MockSignalService.RoomInfo> rooms;

    public TalkServiceTest() {
        super(TalkService.class);
    }

    public void setUp() throws Exception {
        super.setUp();

        rooms = ImmutableMap.of(
                GROUP_ID_1, new MockSignalService.RoomInfo(new Room(1, 2, "localhost", 8000), true),
                GROUP_ID_2, new MockSignalService.RoomInfo(new Room(2, 2, "localhost", 8000), true),
                GROUP_ID_3, new MockSignalService.RoomInfo(new Room(3, 2, "localhost", 8000), false)
        );
        mockSignalService = new MockSignalService(rooms);
        mockTalkEngineFactory = new MockTalkEngineFactory();

        setApplication(new App() {
            @Override
            public SignalService providesSignalService() {
                return mockSignalService;
            }

            @Override
            public TalkEngineFactory providesTalkEngineFactory() {
                return mockTalkEngineFactory;
            }
        });
    }

    public void testJoinRoom() {
        final TalkBinder binder = (TalkBinder) bindService(TalkService.buildConnectIntent(GROUP_ID_1));
        assertEquals(TalkBinder.ROOM_STATUS_CONNECTED, binder.getCurrRoomStatus());
        assertEquals(rooms.get(GROUP_ID_1).room, mockTalkEngineFactory.aliveEngines.get(0).room);
        assertTrue(mockSignalService.rooms.get(GROUP_ID_1).joined);
    }

    public void testQuitRoom() {
        final TalkBinder binder = (TalkBinder) bindService(TalkService.buildConnectIntent(GROUP_ID_1));
        startService(TalkService.buildDisconnectIntent(GROUP_ID_1));
        assertEquals(TalkBinder.ROOM_STATUS_NOT_CONNECTED, binder.getCurrRoomStatus());
        assertTrue(mockTalkEngineFactory.aliveEngines.isEmpty());
        assertFalse(mockSignalService.rooms.get(GROUP_ID_1).joined);
    }

    public void testRequestFocus() {
        final TalkBinder binder = (TalkBinder) bindService(TalkService.buildConnectIntent(GROUP_ID_2));
        startService(TalkService.buildSetAudioFocusIntent(GROUP_ID_2, true));
        assertEquals(TalkBinder.ROOM_STATUS_ACTIVE, binder.getCurrRoomStatus());
        assertTrue(mockSignalService.rooms.get(GROUP_ID_2).hasFocus);
        startService(TalkService.buildSetAudioFocusIntent(GROUP_ID_2, false));
        assertNotSame(TalkBinder.ROOM_STATUS_ACTIVE, binder.getCurrRoomStatus());
        assertFalse(mockSignalService.rooms.get(GROUP_ID_2).hasFocus);
    }
}
