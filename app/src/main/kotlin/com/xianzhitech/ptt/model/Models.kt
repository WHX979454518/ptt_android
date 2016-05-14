package com.xianzhitech.ptt.model

import java.io.Serializable

interface Model : Serializable {
    val id : String
    val name : String
}

interface Room : Model {
    val description : String?
    val ownerId : String
    val associatedGroupIds : Iterable<String>
    val extraMemberIds : Iterable<String>
}

interface Group : Model {
    val description: String?
    val avatar: String?
    val memberIds : Iterable<String>
}

interface User : Model {
    val avatar: String?
    val permissions: Set<Permission>
    val priority: Int
}
