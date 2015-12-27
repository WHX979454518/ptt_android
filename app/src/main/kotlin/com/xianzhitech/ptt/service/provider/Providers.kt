package com.xianzhitech.ptt.service.provider

import android.support.annotation.CheckResult
import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.model.Room
import rx.Observable
import java.io.Serializable

/**
 * 请求会话的参数
 */
interface ConversationRequest : Serializable

/**
 * 从已有的会话发起会话
 */
data class ExistingConversationRequest(val conversationId: String) : ConversationRequest

/**
 * 新建一个会话的请求
 */
interface CreateConversationRequest : ConversationRequest

/**
 * 从一个联系人创建会话请求
 */
data class CreatePersonConversationRequest(val personId: String) : CreateConversationRequest

/**
 * 从一个组创建会话请求
 */
data class CreateGroupConversationRequest(val groupId: String) : CreateConversationRequest

/**
 * 信号服务器的接口
 */
interface SignalProvider {

    /**
     * 创建一个会话
     */
    @CheckResult
    fun createConversation(requests: Iterable<CreateConversationRequest>) : Observable<Conversation>

    /**
     * 删除一个会话
     */
    @CheckResult
    fun deleteConversation(conversationId : String) : Observable<Void>

    /**
     * 加入会话（进入对讲房间）
     */
    @CheckResult
    fun joinConversation(conversationId: String) : Observable<Room>

    /**
     * 退出会话（退出对讲房间）
     */
    @CheckResult
    fun quitConversation(conversationId: String) : Observable<Void>

    /**
     * 抢麦
     */
    @CheckResult
    fun requestMic(conversationId: String): Observable<Boolean>

    /**
     * 释放麦
     */
    @CheckResult
    fun releaseMic(conversationId: String) : Observable<Void>
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
    fun getLogonPersonId(): String?

    /**
     * 登出
     */
    @CheckResult
    fun logout() : Observable<Void>
}