package com.xianzhitech.ptt.service.provider

import com.xianzhitech.ptt.ext.toObservable
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.model.Privilege
import rx.Observable
import java.io.Serializable
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Created by fanchao on 9/01/16.
 */
class MockAuthProvider : AuthProvider {
    companion object {
        public val LEGIT_USER = Person("1", "user", EnumSet.allOf(Privilege::class.java))
    }

    val logonUser = AtomicReference<Person>()

    override fun login(username: String, password: String): Observable<LoginResult> {
        if (username == LEGIT_USER.name) {
            return LoginResult(LEGIT_USER, username).apply {
                logonUser.set(this.person)
            }.toObservable()
        } else {
            logonUser.set(null)
            return Observable.error(RuntimeException())
        }
    }

    override fun resumeLogin(token: Serializable): Observable<LoginResult> {
        if (token == LEGIT_USER.name) {
            return LoginResult(LEGIT_USER, token).apply {
                logonUser.set(this.person)
            }.toObservable()
        } else {
            logonUser.set(null)
            return Observable.error(IllegalAccessError())
        }
    }

    override fun peekCurrentLogonUserId(): String? {
        return logonUser.get()?.id
    }

    override fun logout(): Observable<Unit> {
        logonUser.set(null)
        return Observable.just(null)
    }
}