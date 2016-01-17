package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomWithMemberNames
import com.xianzhitech.ptt.service.provider.CreateRoomRequest
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

data class ExtraRoomState(val roomState: RoomState,
                          val room: RoomWithMemberNames? = null)

data class ExtraLoginState(val loginState: LoginState,
                           val logonUser: User? = null)

interface BackgroundServiceBinder {
    val roomState : Observable<RoomState>
    val loginState : Observable<LoginState>

    fun peekRoomState() : RoomState
    fun peekLoginState() : LoginState

    fun login(username: String, password: String): Observable<Unit>
    fun logout(): Observable<Unit>

    fun createRoom(request: CreateRoomRequest): Observable<String>

    fun requestJoinRoom(roomId: String): Observable<Unit>
    fun requestMic(): Observable<Unit>
    fun releaseMic() : Observable<Unit>
    fun requestQuitCurrentRoom(): Observable<Unit>
}

