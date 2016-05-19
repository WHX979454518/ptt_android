package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.ext.sizeAtLeast
import rx.Completable
import rx.Observable
import java.io.Serializable
import java.util.*


data class CreateRoomRequest(val name : String? = null,
                             val groupIds : Iterable<String> = emptyList(),
                             val extraMemberIds : Iterable<String> = emptyList()) {
    init {
        if (groupIds.sizeAtLeast(1).not() && extraMemberIds.sizeAtLeast(1).not()) {
            throw IllegalArgumentException("GroupId and MemberIds can't be null in the same time");
        }
    }
}

interface RoomInvitation : Serializable {
    val roomId : String
    val inviterId : String
    val inviteTime : Date
}

interface SignalService {
    val roomState : Observable<RoomState>
    val loginState : Observable<LoginState>

    fun peekRoomState() : RoomState
    fun peekLoginState() : LoginState

    fun login(username: String, password: String): Observable<Unit>
    fun logout(): Observable<Unit>

    fun createRoom(request: CreateRoomRequest): Observable<String>

    fun joinRoom(roomId: String): Observable<Unit>
    fun requestMic(): Observable<Unit>
    fun releaseMic() : Observable<Unit>
    fun quitRoom(): Observable<Unit>

    fun changePassword(oldPassword: String,
                       newPassword: String): Completable

    companion object {
        const val ACTION_INVITE_TO_JOIN = "acton_invite_to_join"
        const val EXTRA_INVITE = "extra_invite"
    }
}

val SignalService.roomStatus : Observable<RoomStatus>
get() = roomState.distinctUntilChanged { it.status }.map { it.status }

val SignalService.loginStatus : Observable<LoginStatus>
get() = loginState.distinctUntilChanged { it.status }.map { it.status }