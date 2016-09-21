package com.xianzhitech.ptt.service.dto


interface RoomOnlineMemberUpdate {
    val roomId: String
    val memberIds: Collection<String>
}