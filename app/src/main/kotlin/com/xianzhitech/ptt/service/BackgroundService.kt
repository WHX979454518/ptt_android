package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.service.provider.JoinRoomRequest
import rx.Observable
import kotlin.collections.emptySet

data class RoomState(val status : RoomState.Status = RoomState.Status.IDLE,
                     val currentRoomID: String? = null,
                     val currentRoomActiveSpeakerID: String? = null,
                     val currentRoomOnlineMemberIDs: Set<String> = emptySet()) {
    enum class Status {
        IDLE,
        JOINING,
        JOINED,
        REQUESTING_MIC,
    }
}

data class LoginState(val status : LoginState.Status = LoginState.Status.IDLE,
                      val currentUserID: String? = null) {
    enum class Status {
        IDLE,
        LOGIN_IN_PROGRESS,
        LOGGED_IN,
    }
}

interface BackgroundServiceBinder {
    val roomState : Observable<RoomState>
    val loginState : Observable<LoginState>

    fun peekRoomState() : RoomState
    fun peekLoginState() : LoginState

    fun login(username: String, password: String): Observable<Unit>
    fun logout(): Observable<Unit>

    fun requestJoinRoom(request: JoinRoomRequest): Observable<Unit>
    fun requestMic(): Observable<Unit>
    fun releaseMic() : Observable<Unit>
    fun requestQuitCurrentRoom(): Observable<Unit>
}

