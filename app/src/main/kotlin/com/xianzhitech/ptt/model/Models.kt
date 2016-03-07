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

data class MutableRoom(override var id: String = "",
                       override var name: String = "",
                       override var description: String? = null,
                       override var ownerId: String = "",
                       override var important: Boolean = false,
                       override var lastActiveUserId : String? = null,
                       override var lastActiveTime : Date? = null) : Room

fun Room.toMutable() = MutableRoom(id, name, description, ownerId, important, lastActiveUserId, lastActiveTime)

interface Group {
    val id: String
    val description: String?
    val name : String
    val avatar: String?
}

data class MutableGroup(override var id: String = "",
                        override var description: String? = "",
                        override var name: String = "",
                        override var avatar: String? = "") : Group

fun Group.toMutable() = MutableGroup(id, description, name, avatar)

interface User {
    val id: String
    val name : String
    val avatar: String?
    val privileges : EnumSet<Privilege>
}

data class MutableUser(override var id: String = "",
                       override var name: String = "",
                       override var avatar: String? = null,
                       override var privileges: EnumSet<Privilege> = EnumSet.noneOf(Privilege::class.java)) : User

fun User.toMutable() = MutableUser(id, name, avatar, privileges)

