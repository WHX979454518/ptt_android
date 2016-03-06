package com.xianzhitech.ptt.service.sio

import android.support.v4.util.ArrayMap
import com.xianzhitech.ptt.ext.logd
import com.xianzhitech.ptt.ext.onSingleValue
import com.xianzhitech.ptt.ext.toStringIterable
import com.xianzhitech.ptt.ext.transform
import com.xianzhitech.ptt.model.*
import com.xianzhitech.ptt.service.EmptyServerResponseException
import com.xianzhitech.ptt.service.ServerException
import com.xianzhitech.ptt.service.provider.CreateRoomFromGroup
import com.xianzhitech.ptt.service.provider.CreateRoomFromUser
import com.xianzhitech.ptt.service.provider.CreateRoomRequest
import io.socket.client.Ack
import io.socket.client.Manager
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONObject
import rx.Observable
import rx.subscriptions.Subscriptions
import java.util.*


internal fun Array<Any?>.ensureNoError() {
    val arg = getOrNull(0)

    if (arg is JSONObject && arg.has("error")) {
        throw ServerException(arg.getString("error"))
    }
}

internal fun CreateRoomRequest.toJSON(): JSONArray {
    // 0代表通讯组 1代表联系人
    return when (this) {
        is CreateRoomFromUser -> JSONArray().put(JSONObject().put("srcType", 1).put("srcData", userId))
        is CreateRoomFromGroup -> JSONArray().put(JSONObject().put("srcType", 0).put("srcData", groupId))
        else -> throw IllegalArgumentException("Unknown request type: " + this)
    }
}

internal data class SyncContactsDTO(val groups: Iterable<Group>,
                                    val users: Iterable<User>,
                                    val groupMemberMap: Map<String, Iterable<String>>)

internal fun JSONObject.toSyncContactsDto(): SyncContactsDTO {
    val groupJsonArray = getJSONObject("enterpriseGroups").getJSONArray("add")
    return SyncContactsDTO(
            groupJsonArray.transform { MutableGroup().readFrom(it as JSONObject) },
            getJSONObject("enterpriseMembers").getJSONArray("add").transform { MutableUser().readFrom(it as JSONObject) },
            groupJsonArray.toGroupsAndMembers())
}

internal fun MutableGroup.readFrom(obj: JSONObject): MutableGroup {
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

internal fun MutableRoom.readFrom(obj: JSONObject): MutableRoom {
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
    for (i in 0..size - 1) {
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

internal fun Manager.receiveEvent(eventName: String) = Observable.create<Array<Any>> { subscribe ->
    subscribe.onStart()
    val listener = { args: Array<Any> ->
        subscribe.onNext(args)
    }
    on(eventName, listener)
    subscribe.add(Subscriptions.create { off(eventName, listener) })
}

internal fun Socket.receiveEvent(eventName: String) = Observable.create<JSONObject> { subscriber ->
    subscriber.onStart()
    val listener = { args: Array<Any> ->
        try {
            logd("Received server event $eventName with value $args")
            val value = args.getOrNull(0) as? JSONObject ?: throw EmptyServerResponseException()
            subscriber.onNext(value)
        } catch(e: Exception) {
            subscriber.onError(e)
        }
    }
    on(eventName, listener)
    subscriber.add(Subscriptions.create {
        logd("Unregister listening to event $eventName")
        off(eventName, listener)
    })
}

internal fun Socket.sendEvent(eventName: String, arg: Any? = null) = Observable.create<JSONObject> { subscriber ->
    logd("Sending event $eventName with arg $arg")
    emit(eventName, arrayOf(arg),
            Ack {
                try {
                    it.ensureNoError()
                    val value = (it.getOrNull(0) as? JSONObject) ?: throw EmptyServerResponseException()
                    logd("Received $value for event $eventName with arg $arg")
                    subscriber.onSingleValue(value)
                } catch(e: Exception) {
                    subscriber.onError(e)
                }
            })
}

internal fun Socket.sendEventIgnoreReturn(eventName: String, arg: Any? = null) = Observable.create<Unit> { subscriber ->
    logd("Sending event $eventName with arg $arg ignoring result")
    emit(eventName, arrayOf(arg),
            Ack {
                try {
                    it.ensureNoError()
                    subscriber.onSingleValue(Unit)
                } catch(e: Exception) {
                    subscriber.onError(e)
                }
            })
}