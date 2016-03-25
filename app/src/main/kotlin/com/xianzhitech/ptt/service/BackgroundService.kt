package com.xianzhitech.ptt.service

import android.app.Notification
import com.xianzhitech.ptt.service.provider.CreateRoomRequest
import rx.Observable

data class RoomState(val status : RoomState.Status = RoomState.Status.IDLE,
                     val currentRoomID: String? = null,
                     val currentRoomActiveSpeakerID: String? = null,
                     val currentRoomOnlineMemberIDs: Set<String> = emptySet()) {
    enum class Status(val inRoom : Boolean) {
        IDLE(false),
        JOINING(true),
        JOINED(true),
        REQUESTING_MIC(true),
        ACTIVE(true),
        OFFLINE(false),
    }
}

data class LoginState(val status : LoginState.Status = LoginState.Status.IDLE,
                      val currentUserID: String? = null) {
    enum class Status {
        IDLE,
        LOGIN_IN_PROGRESS,
        LOGGED_IN,
        OFFLINE,
    }
}

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

    fun startForeground(notificationId : Int, notification: Notification)
    fun stopForeground(removeNotification : Boolean)
}

