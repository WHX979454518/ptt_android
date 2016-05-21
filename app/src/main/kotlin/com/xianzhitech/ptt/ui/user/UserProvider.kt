package com.xianzhitech.ptt.ui.user

import android.content.Context
import android.os.Parcelable
import com.xianzhitech.ptt.model.User
import rx.Observable

interface UserProvider : Parcelable {
    fun getUsers(context: Context) : Observable<List<User>>
}