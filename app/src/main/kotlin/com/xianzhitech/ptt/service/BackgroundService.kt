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
    }

    val status : Status
    val activeRoomID: String?
    val activeRoomSpeakerID: String?
    val activeRoomMemberIDs: Set<String>

    val currentJoinRoomRequest: JoinRoomRequest?
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

    fun login(username: String, password: String): Observable<LoginState>
    fun logout(): Observable<Unit>

    fun requestJoinRoom(request: JoinRoomRequest): Observable<Unit>
    fun requestMic(): Observable<Unit>
    fun releaseMic() : Observable<Unit>
    fun requestQuitCurrentRoom(): Observable<Unit>
}

