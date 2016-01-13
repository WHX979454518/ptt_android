package com.xianzhitech.ptt.service.provider

import android.support.annotation.CheckResult
import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.model.Person
import rx.Observable
import java.io.Serializable

/**
 * 请求会话的参数
 */
interface ConversationRequest : Serializable

/**
 * 从已有的会话发起会话
 */
data class ConversationFromExisting(val conversationId: String) : ConversationRequest

/**
 * 新建一个会话的请求
 */
interface CreateConversationRequest : ConversationRequest {
    val name: String?
}

/**
 * 从一个联系人创建会话请求
 */
data class CreateConversationFromPerson(val personId: String, override val name: String? = null) : CreateConversationRequest {
}

/**
 * 从一个组创建会话请求
 */
data class CreateConversationFromGroup(val groupId: String, override val name: String? = null) : CreateConversationRequest

/**
 * 加入房间的返回结果
 */
data class JoinConversationResult(val conversationId: String,
                                  val roomId: String,
                                  val engineProperties: Map<String, Any?>)

/**
 * 信号服务器的接口
 */
interface SignalProvider {

    /**
     * 创建一个会话
     */
    @CheckResult
    fun createConversation(request: CreateConversationRequest): Observable<Conversation>

    /**
     * 删除一个会话
     */
    @CheckResult
    fun deleteConversation(conversationId: String): Observable<Unit>

    /**
     * 加入会话（进入对讲房间）
     */
    @CheckResult
    fun joinConversation(conversationId: String): Observable<JoinConversationResult>

    /**
     * 获取房间在线成员（以及更新）
     */
    fun getActiveMemberIds(conversationId: String): Observable<Collection<String>>

    /**
     * 获取当前房间在线成员
     */
    fun peekActiveMemberIds(conversationId: String): Collection<String>

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
data class LoginResult(val person: Person, val token: Serializable?)

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
    fun peekCurrentLogonUser(): Person?

    /**
     * 订阅当前登陆用户的ID
     */
    @CheckResult
    fun getCurrentLogonUser(): Observable<Person?>

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