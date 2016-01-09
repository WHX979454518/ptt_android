package com.xianzhitech.ptt.repo

import com.xianzhitech.ptt.model.Person
import rx.Observable

/**
 * Created by fanchao on 9/01/16.
 */

interface UserRepositories {
    fun getUser(id: String): Observable<Person>
}