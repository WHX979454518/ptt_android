package com.xianzhitech.ptt.service.provider

import com.xianzhitech.ptt.model.Room
import java.io.Serializable

/**
 * 请求会话的参数
 */
interface JoinRoomRequest : Serializable

/**
 * 从已有的会话发起会话
 */
data class JoinRoomFromExisting(val roomId: String) : JoinRoomRequest

/**
 * 新建一个会话的请求
 */
interface JoinRoomFromContact : JoinRoomRequest {
    val name: String?
}

/**
 * 从一个联系人创建会话请求
 */
data class JoinRoomFromUser(val userId: String, override val name: String? = null) : JoinRoomFromContact {
}

/**
 * 从一个组创建会话请求
 */
data class JoinRoomFromGroup(val groupId: String, override val name: String? = null) : JoinRoomFromContact

/**
 * 加入房间的返回结果
 */
data class JoinRoomResult(val room: Room,
                          val activeMemberIDs: Set<String>,
                          val currentSpeaker: String?,
                          val engineProperties: Map<String, Any?>)


/**
 * 提供程序选项数据的永久存储
 */
interface PreferenceStorageProvider {
    fun save(key: String, value: Serializable?)
    fun remove(key: String)
    fun get(key: String): Serializable?
}