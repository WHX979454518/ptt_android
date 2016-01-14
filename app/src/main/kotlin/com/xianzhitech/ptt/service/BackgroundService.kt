package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.service.provider.JoinRoomRequest
import rx.Observable

interface RoomState {
    enum class Status {
        IDLE,
        JOINING,
        JOINED,
        REQUESTING_MIC,
        ACTIVE,
    }

    val status : Status
    val currentRoomId : String?
    val activeMemberIds : Collection<String>
}

interface LoginState {
    enum class Status {
        IDLE,
        LOGIN_IN_PROGRESS,
        LOGGED_IN,
    }

    val status : Status
    val currentUser : User?
}

interface BackgroundServiceBinder {
    val roomState : Observable<out RoomState>
    val loginState : Observable<out LoginState>

    fun peekRoomState() : RoomState
    fun peekLoginState() : LoginState

    fun login(username : String, password : String) : Observable<out LoginState>
    fun logout()

    fun requestJoinRoom(request: JoinRoomRequest): Observable<out RoomState>
    fun requestMic() : Observable<Boolean>
    fun releaseMic() : Observable<Unit>
    fun requestQuitCurrentRoom()
}

