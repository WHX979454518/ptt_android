package com.xianzhitech.ptt.model

import android.os.Parcel

import android.os.Parcelable
import java.util.*

interface ContactItem

data class GroupContactItem(val groupId : String) : ContactItem
data class UserContactItem(val userId : String) : ContactItem

interface Room : Parcelable {
    val id : String
    val name : String
    val description : String?
    val ownerId : String
    val important : Boolean
    val lastActiveUserId : String?
    val lastActiveTime : Date?
}

interface MutableRoom : Room {
    override var id: String
    override var name: String
    override var description: String?
    override var ownerId: String
    override var important: Boolean
    override var lastActiveUserId: String?
    override var lastActiveTime: Date?
}

interface Group : Parcelable {
    val id: String
    val description: String?
    val name : String
    val avatar: String?
}

interface MutableGroup : Group {
    override var id: String
    override var description: String?
    override var name: String
    override var avatar: String?
}

interface User : Parcelable {
    val id: String
    val name : String
    val avatar: String?
    val privileges : EnumSet<Privilege>
}

interface MutableUser : User {
    override var id: String
    override var name: String
    override var avatar: String?
    override var privileges: EnumSet<Privilege>
}

data class RoomImpl(override var id: String,
                            override var name: String,
                            override var description: String?,
                            override var ownerId: String,
                            override var important: Boolean,
                            override var lastActiveUserId: String?,
                            override var lastActiveTime: Date?) : MutableRoom, Parcelable {
    constructor(source: Parcel): this(source.readString(), source.readString(), source.readSerializable() as String?, source.readString(), 1.toByte().equals(source.readByte()), source.readSerializable() as String?, source.readSerializable() as Date?)

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
        dest.writeString(name)
        dest.writeSerializable(description)
        dest.writeString(ownerId)
        dest.writeByte((if (important) 1 else 0).toByte())
        dest.writeSerializable(lastActiveUserId)
        dest.writeSerializable(lastActiveTime)
    }

    companion object {
        @JvmField final val CREATOR: Parcelable.Creator<RoomImpl> = object : Parcelable.Creator<RoomImpl> {
            override fun createFromParcel(source: Parcel): RoomImpl {
                return RoomImpl(source)
            }

            override fun newArray(size: Int): Array<RoomImpl?> {
                return arrayOfNulls(size)
            }
        }
    }
}

data class GroupImpl(override var id: String,
                             override var description: String?,
                             override var name: String,
                             override var avatar: String?) : MutableGroup, Parcelable {
    constructor(source: Parcel): this(source.readString(), source.readSerializable() as String?, source.readString(), source.readSerializable() as String?)

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
        dest.writeSerializable(description)
        dest.writeString(name)
        dest.writeSerializable(avatar)
    }

    companion object {
        @JvmField final val CREATOR: Parcelable.Creator<GroupImpl> = object : Parcelable.Creator<GroupImpl> {
            override fun createFromParcel(source: Parcel): GroupImpl {
                return GroupImpl(source)
            }

            override fun newArray(size: Int): Array<GroupImpl?> {
                return arrayOfNulls(size)
            }
        }
    }
}

data class UserImpl(override var id: String,
                            override var name: String,
                            override var avatar: String?,
                            var privilegesText: String?) : MutableUser, Parcelable {
    override var privileges: EnumSet<Privilege>
    get() = privilegesText.toPrivileges()
    set(value) {
        privilegesText = value.toDatabaseString()
    }

    constructor(source: Parcel): this(source.readString(), source.readString(), source.readSerializable() as String?, source.readSerializable() as String?)

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
        dest.writeString(name)
        dest.writeSerializable(avatar)
        dest.writeSerializable(privilegesText)
    }

    companion object {
        @JvmField final val CREATOR: Parcelable.Creator<UserImpl> = object : Parcelable.Creator<UserImpl> {
            override fun createFromParcel(source: Parcel): UserImpl {
                return UserImpl(source)
            }

            override fun newArray(size: Int): Array<UserImpl?> {
                return arrayOfNulls(size)
            }
        }
    }
}