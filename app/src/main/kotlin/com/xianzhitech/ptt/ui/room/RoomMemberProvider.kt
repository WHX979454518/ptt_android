package com.xianzhitech.ptt.ui.room

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.ui.user.UserProvider
import rx.Observable

class RoomMemberProvider(val roomId : String, val excludeUserIds : Array<String?> = arrayOf()) : UserProvider, Parcelable {
    override fun getUsers(context: Context): Observable<List<User>> {
        return (context.applicationContext as AppComponent).roomRepository
                .getRoomMembers(roomId, excludeUserIds = excludeUserIds, maxMemberCount = Int.MAX_VALUE)
                .observe()
    }

    constructor(source: Parcel): this(source.readString())

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(roomId)
    }

    companion object {
        @JvmField final val CREATOR: Parcelable.Creator<RoomMemberProvider> = object : Parcelable.Creator<RoomMemberProvider> {
            override fun createFromParcel(source: Parcel): RoomMemberProvider {
                return RoomMemberProvider(source)
            }

            override fun newArray(size: Int): Array<RoomMemberProvider?> {
                return arrayOfNulls(size)
            }
        }
    }
}