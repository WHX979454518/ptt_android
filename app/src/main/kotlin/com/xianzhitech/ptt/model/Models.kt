package com.xianzhitech.ptt.model

import java.io.Serializable

interface Model : Serializable {
    val id : String
    val name : String
}

interface Room : Model {
    val description : String?
    val ownerId : String
    val associatedGroupIds : Collection<String>
    val extraMemberIds : Collection<String>
}

interface Group : Model {
    val description: String?
    val avatar: String?
    val memberIds : Collection<String>
}

interface User : Model {
    val avatar: String?
    val permissions: Set<Permission>
    val priority: Int
    val phoneNumber : String?
    val enterpriseId : String
    val enterpriseName : String
}
