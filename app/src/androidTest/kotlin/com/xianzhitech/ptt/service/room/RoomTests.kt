package com.xianzhitech.ptt.service.room

import android.test.ServiceTestCase
import com.xianzhitech.ptt.App
import com.xianzhitech.ptt.service.provider.AuthProvider

/**
 * Created by fanchao on 18/12/15.
 */

class RoomServiceTest : ServiceTestCase<RoomService>(RoomService::class.java) {

    override fun setUp() {
        super.setUp()

        application = object : App() {
            override fun providesAuth(): AuthProvider? {
                return super.providesAuth()
            }
        }
    }
}