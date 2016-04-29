package com.xianzhitech.ptt.service

import rx.Observable
import java.io.Serializable
import java.util.*


/**
 * 新建一个会话的请求
 */
interface CreateRoomRequest {
    val name: String?
}

/**
 * 从一个联系人创建会话请求
 */
data class CreateRoomFromUser(val userId: String,
                              override val name: String? = null) : CreateRoomRequest

/**
 * 从一个组创建会话请求
 */
data class CreateRoomFromGroup(val groupId: String,
                               override val name: String? = null) : CreateRoomRequest

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

    fun changePassword(verifyOldPassword : Boolean,
                       oldPassword : String?,
                       newPassword : String) : Observable<Unit>

    companion object {
        const val ACTION_INVITE_TO_JOIN = "acton_invite_to_join"
        const val EXTRA_INVITE = "extra_invite"
    }
}

val SignalService.roomStatus : Observable<RoomStatus>
get() = roomState.distinctUntilChanged { it.status }.map { it.status }

val SignalService.loginStatus : Observable<LoginStatus>
get() = loginState.distinctUntilChanged { it.status }.map { it.status }