package com.xianzhitech.ptt.api.event

import android.os.Parcel
import android.os.Parcelable
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.xianzhitech.ptt.data.Room
import java.util.*


data class WalkieRoomInvitationEvent(
        @param:JsonProperty("room")
        val room: Room,

        @param:JsonProperty("inviterId")
        val inviterId: String,

        @param:JsonProperty("inviterPriority")
        val inviterPriority: Int,

        @param:JsonProperty("force")
        val force: Boolean
) : Event, Parcelable {

    @get:JsonIgnore
    var inviteTime = Date()
    private set

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<WalkieRoomInvitationEvent> = object : Parcelable.Creator<WalkieRoomInvitationEvent> {
            override fun createFromParcel(source: Parcel): WalkieRoomInvitationEvent = WalkieRoomInvitationEvent(source)
            override fun newArray(size: Int): Array<WalkieRoomInvitationEvent?> = arrayOfNulls(size)
        }
    }

    constructor(source: Parcel) : this(source.readParcelable<Room>(Room::class.java.classLoader), source.readString(), source.readInt(), 1.equals(source.readInt())) {
        inviteTime = Date(source.readLong())
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeParcelable(room, 0)
        dest?.writeString(inviterId)
        dest?.writeInt(inviterPriority)
        dest?.writeInt((if (force) 1 else 0))
        dest?.writeLong(inviteTime.time)
    }
}