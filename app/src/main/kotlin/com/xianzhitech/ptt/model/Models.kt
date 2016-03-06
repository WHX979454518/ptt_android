package com.xianzhitech.ptt.model

import java.util.*

interface ContactItem

data class GroupContactItem(val groupId : String) : ContactItem
data class UserContactItem(val userId : String) : ContactItem

interface Room {
    var id : String
    var name : String
    var description : String?
    var ownerId : String
    var important : Boolean
}

data class MutableRoom(override var id: String = "",
                       override var name: String = "",
                       override var description: String? = null,
                       override var ownerId: String = "",
                       override var important: Boolean = false) : Room

fun Room.toMutable() = MutableRoom(id, name, description, ownerId, important)

interface Group {
    var id: String
    var description: String?
    var name : String
    var avatar: String?
}

data class MutableGroup(override var id: String = "",
                        override var description: String? = "",
                        override var name: String = "",
                        override var avatar: String? = "") : Group

fun Group.toMutable() = MutableGroup(id, description, name, avatar)

interface User {
    var id: String
    var name : String
    var avatar: String?
    var privileges : EnumSet<Privilege>
}

data class MutableUser(override var id: String = "",
                       override var name: String = "",
                       override var avatar: String? = null,
                       override var privileges: EnumSet<Privilege> = EnumSet.noneOf(Privilege::class.java)) : User

fun User.toMutable() = MutableUser(id, name, avatar, privileges)

