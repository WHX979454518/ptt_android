package com.xianzhitech.ptt.service.provider

import android.support.annotation.CheckResult
import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.model.Room
import rx.Observable

/**
 * 创建房间的参数
 */
data class CreateConversationRequest private constructor(val personId : String?, val groupId : String?) {
    companion object {
        /**
         * 从一个联系人创建
         */
        public @JvmStatic fun fromPerson(personId : String) = CreateConversationRequest(personId, null)

        /**
         * 从一个联系人组创建
         */
        public @JvmStatic fun fromGroup(groupId : String) = CreateConversationRequest(null, groupId)
    }
}

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
    fun requestFocus(roomId: Int): Observable<Boolean>

    /**
     * 释放麦
     */
    @CheckResult
    fun releaseFocus(roomId: Int) : Observable<Void>
}

/**
 * 提供鉴权服务
 */
interface AuthProvider {
    /**
     * 登陆
     */
    @CheckResult
    fun login(username: String, password: String): Observable<Person>

    /**
     * 登出
     */
    @CheckResult
    fun logout() : Observable<Void>
}