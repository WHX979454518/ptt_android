package com.xianzhitech.ptt.ui.user

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.model.User
import rx.Observable

class ContactUserProvider() : UserProvider, Parcelable {
    override fun getUsers(context: Context): Observable<List<User>> {
        return (context.applicationContext as AppComponent).contactRepository.getAllContactUsers().observe()
    }

    constructor(source: Parcel) : this()

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
    }

    companion object {
        @JvmField final val CREATOR: Parcelable.Creator<ContactUserProvider> = object : Parcelable.Creator<ContactUserProvider> {
            override fun createFromParcel(source: Parcel): ContactUserProvider {
                return ContactUserProvider(source)
            }

            override fun newArray(size: Int): Array<ContactUserProvider?> {
                return arrayOfNulls(size)
            }
        }
    }
}