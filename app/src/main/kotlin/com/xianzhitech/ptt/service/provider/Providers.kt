package com.xianzhitech.ptt.service.provider


/**
 * 新建一个会话的请求
 */
interface CreateRoomRequest {
    val name: String?
}

/**
 * 从一个联系人创建会话请求
 */
data class CreateRoomFromUser(val userId: String, override val name: String? = null) : CreateRoomRequest {
}

/**
 * 从一个组创建会话请求
 */
data class CreateRoomFromGroup(val groupId: String, override val name: String? = null) : CreateRoomRequest


/**
 * 提供程序选项数据的永久存储
 */
interface PreferenceStorageProvider {
    var userSessionToken: String?
    var lastLoginUserId: String?
}