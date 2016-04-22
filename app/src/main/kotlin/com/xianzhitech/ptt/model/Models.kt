package com.xianzhitech.ptt.model

import java.io.Serializable
import java.util.*

interface ContactItem

data class GroupContactItem(val groupId : String) : ContactItem
data class UserContactItem(val userId : String) : ContactItem

interface Room : Serializable {
    val id : String
    val name : String
    val description : String?
    val ownerId : String
    val lastActiveUserId : String?
    val lastActiveTime : Date?
}

interface MutableRoom : Room {
    override var id: String
    override var name: String
    override var description: String?
    override var ownerId: String
    override var lastActiveUserId: String?
    override var lastActiveTime: Date?
}

interface Group : Serializable {
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

interface User : Serializable {
    val id: String
    val name : String
    val avatar: String?
    val privileges : EnumSet<Privilege>
    val level : Int
}

interface MutableUser : User {
    override var id: String
    override var name: String
    override var avatar: String?
    override var privileges: EnumSet<Privilege>
    override var level: Int
}

data class RoomImpl(override var id: String,
                    override var name: String,
                    override var description: String?,
                    override var ownerId: String,
                    override var lastActiveUserId: String?,
                    override var lastActiveTime: Date?) : MutableRoom

data class GroupImpl(override var id: String,
                     override var description: String?,
                     override var name: String,
                     override var avatar: String?) : MutableGroup

data class UserImpl(override var id: String,
                    override var name: String,
                    override var level : Int,
                    override var avatar: String?,
                    override var privileges: EnumSet<Privilege>) : MutableUser