package com.xianzhitech.ptt.service.provider

import android.support.annotation.CheckResult
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import rx.Observable
import java.io.Serializable

/**
 * 请求会话的参数
 */
interface JoinRoomRequest : Serializable

/**
 * 从已有的会话发起会话
 */
data class JoinRoomFromExisting(val roomId: String) : JoinRoomRequest

/**
 * 新建一个会话的请求
 */
interface JoinRoomFromContact : JoinRoomRequest {
    val name: String?
}

/**
 * 从一个联系人创建会话请求
 */
data class JoinRoomFromUser(val userId: String, override val name: String? = null) : JoinRoomFromContact {
}

/**
 * 从一个组创建会话请求
 */
data class JoinRoomFromGroup(val groupId: String, override val name: String? = null) : JoinRoomFromContact

/**
 * 加入房间的返回结果
 */
data class JoinRoomResult(val roomId: String,
                          val engineProperties: Map<String, Any?>)

/**
 * 信号服务器的接口
 */
interface SignalProvider {

    /**
     * 创建一个会话
     */
    @CheckResult
    fun createRoom(request: JoinRoomFromContact): Observable<Room>

    /**
     * 删除一个会话
     */
    @CheckResult
    fun deleteRoom(roomId: String): Observable<Unit>

    /**
     * 加入会话（进入对讲房间）
     */
    @CheckResult
    fun joinRoom(roomId: String): Observable<JoinRoomResult>

    /**
     * 获取房间在线成员（以及更新）
     */
    fun getActiveMemberIds(roomId: String): Observable<Collection<String>>

    /**
     * 获取当前房间在线成员
     */
    fun peekActiveMemberIds(roomId: String): Collection<String>

    /**
     * 获取正在发言的成员（以及后续的更新）
     */
    fun getCurrentSpeakerId(conversationId: String): Observable<String?>

    /**
     * 获取当前正在发言的成员
     */
    fun peekCurrentSpeakerId(conversationId: String): String?

    /**
     * 退出会话（退出对讲房间）
     */
    @CheckResult
    fun quitConversation(conversationId: String): Observable<Unit>

    /**
     * 抢麦
     */
    @CheckResult
    fun requestMic(conversationId: String): Observable<Boolean>

    /**
     * 释放麦
     */
    @CheckResult
    fun releaseMic(conversationId: String): Observable<Unit>
}

/**
 * 登陆结果
 */
data class LoginResult(val user: User, val token: Serializable?)

/**
 * 提供鉴权服务
 */
interface AuthProvider {
    /**
     * 使用用户名和密码登陆
     */
    @CheckResult
    fun login(username: String, password: String): Observable<LoginResult>

    /**
     * 使用下发的Token登陆
     */
    @CheckResult
    fun resumeLogin(token: Serializable): Observable<LoginResult>

    /**
     * 获取当前登陆的用户ID
     */
    fun peekCurrentLogonUser(): User?

    /**
     * 订阅当前登陆用户的ID
     */
    @CheckResult
    fun getCurrentLogonUser(): Observable<User?>

    /**
     * 登出
     */
    @CheckResult
    fun logout(): Observable<Unit>
}

/**
 * 提供程序选项数据的永久存储
 */
interface PreferenceStorageProvider {
    fun save(key: String, value: Serializable?)
    fun remove(key: String)
    fun get(key: String): Serializable?
}