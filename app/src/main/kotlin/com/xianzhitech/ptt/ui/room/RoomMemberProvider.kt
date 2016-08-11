package com.xianzhitech.ptt.ui.room

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.model.Model
import com.xianzhitech.ptt.ui.home.BaseModelProvider
import rx.Observable

class RoomMemberProvider : BaseModelProvider {

    private val roomId : String
    private val excludeUserIds: Array<String?>

    constructor(roomId: String,
                selectable : Boolean,
                preselectedModelIds : Collection<String> = emptyList(),
                preselectedUnselectable : Boolean = false,
                excludeUserIds: Array<String?> = arrayOf()) : super(selectable, preselectedModelIds, preselectedUnselectable) {
        this.roomId = roomId
        this.excludeUserIds = excludeUserIds
    }

    private constructor(source: Parcel) : super(source) {
        this.roomId = source.readString()
        this.excludeUserIds = source.createStringArray()
    }

    override fun getModels(context: Context): Observable<List<Model>> {
        return (context.applicationContext as AppComponent).roomRepository
                .getRoomMembers(roomId, excludeUserIds = excludeUserIds, maxMemberCount = Int.MAX_VALUE)
                .observe() as Observable<List<Model>>
    }



    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeString(roomId)
        dest.writeStringArray(excludeUserIds)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<RoomMemberProvider> = object : Parcelable.Creator<RoomMemberProvider> {
            override fun createFromParcel(source: Parcel): RoomMemberProvider {
                return RoomMemberProvider(source)
            }

            override fun newArray(size: Int): Array<RoomMemberProvider?> {
                return arrayOfNulls(size)
            }
        }
    }
}