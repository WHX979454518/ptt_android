package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.ext.sizeAtLeast
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.service.dto.*
import rx.Completable
import rx.Observable
import rx.Single
import java.io.Serializable
import java.util.*


data class CreateRoomRequest(val name: String? = null,
                             val groupIds: Iterable<String> = emptyList(),
                             val extraMemberIds: Iterable<String> = emptyList()) {
    init {
        if (groupIds.sizeAtLeast(1).not() && extraMemberIds.sizeAtLeast(1).not()) {
            throw IllegalArgumentException("GroupId and MemberIds can't be null in the same time");
        }
    }
}

interface RoomInvitation : Serializable {
    val roomId: String
    val inviterId: String
    val inviteTime: Date
}

interface ExtraRoomInvitation {
    val room : Room
}

interface LoginResult {
    val status: LoginStatus
    val user: User?
    val token: UserToken?
}

interface TokenProvider {
    val authToken: UserToken?
    val loginName: String?
    val loginPassword: String?
}

interface SignalService {
    fun login(tokenProvider: TokenProvider): Observable<LoginResult>
    fun logout(): Completable
    fun changePassword(tokenProvider: TokenProvider, oldPassword: String, newPassword: String): Single<UserToken>

    fun createRoom(request: CreateRoomRequest): Single<Room>
    fun updateRoomMembers(roomId: String, userIds: Iterable<String>): Single<Room>

    fun joinRoom(roomId: String): Single<JoinRoomResult>
    fun requestMic(roomId: String): Single<Boolean>
    fun releaseMic(roomId: String): Completable
    fun leaveRoom(roomId: String): Completable

    fun retrieveUserKickedOutEvent(): Observable<UserKickedOutEvent>
    fun retrieveRoomOnlineMemberUpdate(): Observable<RoomOnlineMemberUpdate>
    fun retrieveRoomSpeakerUpdate(): Observable<RoomSpeakerUpdate>
    fun retrieveRoomInfo(roomId: String): Single<Room>
    fun retrieveUserInfo(userId: String): Single<User>
    fun retrieveInvitation(): Observable<RoomInvitation>
    fun retrieveContacts(): Single<Contacts>
}
//
//val SignalService.roomState : BehaviorSubject<RoomState>
//    get() = BehaviorSubject.create()
//val SignalService.loginState : BehaviorSubject<LoginState>
//    get() = BehaviorSubject.create()
//
//fun SignalService.peekRoomState() : RoomState {
//    return roomState.value
//}
//fun SignalService.peekLoginState() : LoginState {
//    return loginState.value
//}
//
//val SignalService.roomStatus : Observable<RoomStatus>
//    get() = roomState.distinctUntilChanged { it.status }.map { it.status }
//
//val SignalService.loginStatus : Observable<LoginStatus>
//    get() = loginState.distinctUntilChanged { it.status }.map { it.status }
//
//val SignalService.currentUserId : String?
//    get() = peekLoginState().currentUserID
//
//val SignalService.currentRoomId : String?
//    get() = peekRoomState().currentRoomId