package com.xianzhitech.ptt.model

import java.util.*

interface ContactItem

data class GroupContactItem(val groupId : String) : ContactItem
data class UserContactItem(val userId : String) : ContactItem

interface Room {
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

interface Group {
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

interface User {
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
                            override var lastActiveTime: Date?) : MutableRoom {
    constructor() : this("", "", null, "", false, null, null)
}

data class GroupImpl(override var id: String,
                             override var description: String?,
                             override var name: String,
                             override var avatar: String?) : MutableGroup {
    constructor() : this("", null, "", null)
}

data class UserImpl(override var id: String,
                            override var name: String,
                            override var avatar: String?,
                            var privilegesText: String?) : MutableUser {

    constructor() : this("", "", null, null)


    override var privileges: EnumSet<Privilege>
        get() = privilegesText.toPrivileges()
        set(value) {
            privilegesText = value.toDatabaseString()
        }
}