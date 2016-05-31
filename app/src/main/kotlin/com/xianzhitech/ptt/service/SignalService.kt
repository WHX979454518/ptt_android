package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.ext.sizeAtLeast
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import rx.Completable
import rx.Observable
import rx.Single
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

    fun retrieveInvitation() : Observable<RoomInvitation>

    fun login(loginName: String, password: String): Completable
    fun logout(): Completable

    fun createRoom(request: CreateRoomRequest): Single<Room>

    fun joinRoom(roomId: String): Completable
    fun requestMic(): Single<Boolean>
    fun releaseMic(): Completable
    fun leaveRoom(): Completable

    fun updateRoomMembers(roomId : String, userIds : Iterable<String>) : Completable

    fun retrieveRoomInfo(roomId : String): Single<Room>
    fun retrieveUserInfo(userId : String) : Single<User>

    fun changePassword(oldPassword: String,
                       newPassword: String): Completable
}

val SignalService.roomStatus : Observable<RoomStatus>
    get() = roomState.distinctUntilChanged { it.status }.map { it.status }

val SignalService.loginStatus : Observable<LoginStatus>
    get() = loginState.distinctUntilChanged { it.status }.map { it.status }

val SignalService.currentUserId : String?
    get() = peekLoginState().currentUserID

val SignalService.currentRoomId : String?
    get() = peekRoomState().currentRoomId