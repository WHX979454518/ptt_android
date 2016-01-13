package com.xianzhitech.ptt.service.provider

import com.xianzhitech.ptt.ext.toObservable
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.model.Privilege
import rx.Observable
import rx.subjects.BehaviorSubject
import java.io.Serializable
import java.util.*

/**
 * Created by fanchao on 9/01/16.
 */
class MockAuthProvider : AuthProvider {
    companion object {
        public val LEGIT_USER = Person("1", "user", EnumSet.allOf(Privilege::class.java))
    }

    val logonUser = BehaviorSubject.create(null as Person?)

    override fun login(username: String, password: String): Observable<LoginResult> {
        if (username == LEGIT_USER.name) {
            return LoginResult(LEGIT_USER, username).apply {
                logonUser.onNext(this.person)
            }.toObservable()
        } else {
            logonUser.onNext(null)
            return Observable.error(RuntimeException())
        }
    }

    override fun getCurrentLogonUser() = logonUser.map { it?.id }

    override fun resumeLogin(token: Serializable): Observable<LoginResult> {
        if (token == LEGIT_USER.name) {
            return LoginResult(LEGIT_USER, token).apply {
                logonUser.onNext(this.person)
            }.toObservable()
        } else {
            logonUser.onNext(null)
            return Observable.error(IllegalAccessError())
        }
    }

    override fun peekCurrentLogonUser(): Person? {
        return logonUser.value?.id
    }

    override fun logout(): Observable<Unit> {
        logonUser.onNext(null)
        return Observable.just(null)
    }
}