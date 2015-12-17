package com.xianzhitech.service.provider

import com.xianzhitech.model.Person
import com.xianzhitech.ptt.service.signal.Room

/**
 * Created by fanchao on 17/12/15.
 */

interface SignalProvider {
    fun joinRoom(groupId: String): Room
    fun quitRoom(groupId: String)
    fun requestFocus(roomId: Int): Boolean
    fun releaseFocus(roomId: Int)
}

interface AuthProvider {
    fun login(username: String, password: String): Person
    fun logout()
}