package com.xianzhitech.ptt

import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.model.Privilege
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.service.provider.AuthProvider
import com.xianzhitech.ptt.service.provider.CreateConversationRequest
import com.xianzhitech.ptt.service.provider.LoginResult
import com.xianzhitech.ptt.service.provider.SignalProvider
import rx.Observable
import rx.subjects.BehaviorSubject
import java.io.Serializable
import java.util.*

/**
 * Created by fanchao on 27/12/15.
 */

class DevAuthProvider : AuthProvider, SignalProvider {
    private var logonUserSubject = BehaviorSubject.create<Person>()

    @Synchronized
    override fun login(username: String, password: String): Observable<LoginResult> {
        if (!logonUserSubject.hasValue()) {
            logonUserSubject.onNext(Person("id", username, EnumSet.allOf(Privilege::class.java)))
        }

        return logonUserSubject.map { LoginResult(it, username) }
    }

    override fun resumeLogin(token: Serializable) = login(token as String, "")

    override fun getLogonPersonId() = logonUserSubject.value?.id

    override fun logout(): Observable<Void> {
        logonUserSubject = BehaviorSubject.create()
        return Observable.just(null)
    }

    override fun createConversation(requests: Iterable<CreateConversationRequest>): Observable<Conversation> {
        throw UnsupportedOperationException()
    }

    override fun deleteConversation(conversationId: String): Observable<Void> {
        throw UnsupportedOperationException()
    }

    override fun joinConversation(conversationId: String): Observable<Room> {
        throw UnsupportedOperationException()
    }

    override fun quitConversation(conversationId: String): Observable<Void> {
        throw UnsupportedOperationException()
    }

    override fun requestMic(conversationId: String): Observable<Boolean> {
        throw UnsupportedOperationException()
    }

    override fun releaseMic(conversationId: String): Observable<Void> {
        throw UnsupportedOperationException()
    }
}