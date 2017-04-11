package com.xianzhitech.ptt.model

import org.json.JSONObject
import java.util.*

interface Model {
    val id: String
}

interface NamedModel : Model {
    val name: String
}

interface Room : NamedModel {
    val description: String?
    val ownerId: String
    val associatedGroupIds: Collection<String>
    val extraMemberIds: Collection<String>
}

interface Group : NamedModel {
    val description: String?
    val avatar: String?
    val memberIds: Collection<String>
}

interface User : NamedModel {
    val avatar: String?
    val permissions: Set<Permission>
    val priority: Int
    val phoneNumber: String?
    val enterpriseId: String
    val enterpriseName: String
    val enterpriseExpireDate: Date?
}

data class Message(override val id : String,
                   val remoteId: String?,
                   val senderId: String,
                   val sendTime : Long,
                   val roomId : String,
                   val read: Boolean,
                   val type: String,
                   val body: JSONObject) : Model {
    fun bodyAsText() : String? {
        return body.optString("text", null)
    }
}
