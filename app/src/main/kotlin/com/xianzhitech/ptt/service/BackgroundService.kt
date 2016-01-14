package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.service.provider.JoinRoomRequest
import rx.Observable
import kotlin.collections.emptySet

data class RoomState(val status : RoomState.Status = RoomState.Status.IDLE,
                     val activeRoomID: String? = null,
                     val activeRoomSpeakerID: String? = null,
                     val activeRoomOnlineMemberIDs: Set<String> = emptySet(),
                     val currentJoinRoomRequest: JoinRoomRequest? = null) {
    enum class Status {
        IDLE,
        JOINING,
        JOINED,
        REQUESTING_MIC,
    }
}

data class LoginState(val status : LoginState.Status = LoginState.Status.IDLE,
                      val currentUser : User? = null) {
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

    fun login(username: String, password: String): Observable<LoginState>
    fun logout(): Observable<Unit>

    fun requestJoinRoom(request: JoinRoomRequest): Observable<Unit>
    fun requestMic(): Observable<Unit>
    fun releaseMic() : Observable<Unit>
    fun requestQuitCurrentRoom(): Observable<Unit>
}

