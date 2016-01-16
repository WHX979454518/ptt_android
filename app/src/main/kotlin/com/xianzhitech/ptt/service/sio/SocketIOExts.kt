package com.xianzhitech.ptt.service.sio

import android.support.v4.util.ArrayMap
import com.xianzhitech.ptt.ext.toStringIterable
import com.xianzhitech.ptt.ext.transform
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Privilege
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.service.EmptyServerResponseException
import com.xianzhitech.ptt.service.ServerException
import com.xianzhitech.ptt.service.provider.JoinRoomFromContact
import com.xianzhitech.ptt.service.provider.JoinRoomFromGroup
import com.xianzhitech.ptt.service.provider.JoinRoomFromUser
import io.socket.client.Ack
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONObject
import rx.Observable
import rx.subscriptions.Subscriptions
import java.util.*
import kotlin.collections.emptyMap
import kotlin.collections.getOrElse
import kotlin.collections.plusAssign

internal data class SyncContactsDto(val groups : Iterable<Group>,
                                    val users : Iterable<User>,
                                    val groupMemberMap : Map<String, Iterable<String>>)

internal fun JSONObject.toSyncContactsDto() : SyncContactsDto {
    val groupJsonArray = getJSONObject("enterpriseGroups").getJSONArray("add")
    return SyncContactsDto(
            groupJsonArray.transform { Group().readFrom(it as JSONObject) },
            getJSONObject("enterpriseMembers").getJSONArray("add").transform { User().readFrom(it as JSONObject) },
            groupJsonArray.toGroupsAndMembers())
}

internal inline fun <T> parseSeverResult(args : Array<Any?>, mapper : (JSONObject) -> T) : T {
    val arg = args.getOrElse(0, { throw EmptyServerResponseException() })

    if (arg is JSONObject && arg.has("error")) {
        throw ServerException(arg.getString("error"))
    }

    return mapper(arg as JSONObject)
}


internal fun <T> Socket.onEvent(event: String, mapper : (JSONObject) -> T) = Observable.create<T> { subscriber ->
    val listener: (Array<Any?>) -> Unit = {
        subscriber.onStart()
        try {
            subscriber.onNext(parseSeverResult(it, mapper))
        } catch(e: Exception) {
            subscriber.onError(e)
        }
    }
    on(event, listener)

    subscriber.add(Subscriptions.create { off(event, listener) })
}

internal fun Socket.onJsonObjectEvent(event: String) = Observable.create<JSONObject> { subscriber ->
    val listener: (Array<Any?>) -> Unit = {
        subscriber.onStart()
        try {
            subscriber.onNext(parseSeverResult(it, { it }))
        } catch(e: Exception) {
            subscriber.onError(e)
        }
    }
    on(event, listener)

    subscriber.add(Subscriptions.create { off(event, listener) })
}


internal fun <T> Socket.sendEvent(eventName: String, resultMapper: (JSONObject) -> T, vararg args: Any?) = Observable.create<T> { subscriber ->
    subscriber.onStart()
    emit(eventName, args, Ack { args: Array<Any?> ->
        try {
            subscriber.onNext(parseSeverResult(args, resultMapper))
        } catch(e: Throwable) {
            subscriber.onError(e)
        } finally {
            subscriber.onCompleted()
        }
    })
}


internal fun JoinRoomFromContact.toJSON(): JSONArray {
    // 0代表通讯组 1代表联系人
    return when (this) {
        is JoinRoomFromUser -> JSONArray().put(JSONObject().put("srcType", 1).put("srcData", userId))
        is JoinRoomFromGroup -> JSONArray().put(JSONObject().put("srcType", 0).put("srcData", groupId))
        else -> throw IllegalArgumentException("Unknown request type: " + this)
    }
}

internal fun Group.readFrom(obj : JSONObject) : Group {
    id = obj.getString("idNumber")
    description = obj.optString("description")
    name = obj.getString("name")
    avatar = obj.optString("avatar")
    return this
}

internal fun User.readFrom(obj: JSONObject): User {
    id = obj.getString("idNumber")
    name = obj.getString("name")
    avatar = obj.optString("avatar")
    privileges = obj.optJSONObject("privileges").toPrivilege()
    return this
}

internal fun Room.readFrom(obj : JSONObject) : Room {
    id = obj.getString("idNumber")
    name = obj.getString("name")
    ownerId = obj.getString("owner")
    important = obj.getBoolean("important")
    return this
}

internal fun JSONArray?.toGroupsAndMembers(): Map<String, Iterable<String>> {
    if (this == null) {
        return emptyMap()
    }

    val size = length()
    val result = ArrayMap<String, Iterable<String>>(size)
    for (i in 1..size - 1) {
        val groupObject = getJSONObject(i)
        result.put(groupObject.getString("idNumber"), groupObject.optJSONArray("members").toStringIterable())
    }

    return result
}


internal fun JSONObject.hasPrivilege(name: String) = has(name) && getBoolean(name)


internal fun JSONObject?.toPrivilege(): EnumSet<Privilege> {
    val result = EnumSet.noneOf(Privilege::class.java)

    this?.let {
        if (hasPrivilege("call")) {
            result += Privilege.MAKE_CALL
        }

        if (hasPrivilege("group")) {
            result += Privilege.CREATE_ROOM
        }

        if (hasPrivilege("recvCall")) {
            result += Privilege.RECEIVE_CALL
        }

        if (hasPrivilege("recvGroup")) {
            result += Privilege.RECEIVE_ROOM
        }
    }

    return result
}
