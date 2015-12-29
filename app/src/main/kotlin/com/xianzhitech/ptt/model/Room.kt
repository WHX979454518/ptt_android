package com.xianzhitech.ptt.model

/**
 *
 * 表示一个房间的内存模型
 *
 * Created by fanchao on 18/12/15.
 */
data class Room(val id: String,
                val conversationId: String,
                val members: Collection<String>,
                val speaker: String?,
                val properties: Map<String, Any>) {
    inline fun <reified T> getProperty(name: String) = properties[name] as T?
}