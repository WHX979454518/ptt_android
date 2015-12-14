package com.podkitsoftware.shoumi;

import android.test.ServiceTestCase;

import com.google.common.collect.ImmutableMap;
import com.podkitsoftware.shoumi.engine.TalkEngineFactory;
import com.podkitsoftware.shoumi.mock.MockSignalService;
import com.podkitsoftware.shoumi.mock.MockTalkEngineFactory;
import com.podkitsoftware.shoumi.model.Room;
import com.podkitsoftware.shoumi.service.signal.SignalService;
import com.podkitsoftware.shoumi.service.talk.TalkService;

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
    private final Map<String, MockSignalService.RoomInfo> rooms = ImmutableMap.of(
            GROUP_ID_1, new MockSignalService.RoomInfo(new Room(1, 2, "localhost", 8000), true),
            GROUP_ID_2, new MockSignalService.RoomInfo(new Room(2, 2, "localhost", 8000), true),
            GROUP_ID_3, new MockSignalService.RoomInfo(new Room(3, 2, "localhost", 8000), false)
    );

    public TalkServiceTest() {
        super(TalkService.class);
    }

    public void setUp() throws Exception {
        super.setUp();

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
        startService(TalkService.buildConnectIntent(GROUP_ID_1));
        assertEquals(rooms.get(GROUP_ID_1).room, mockTalkEngineFactory.createdEngines.get(0).room);
    }
}
