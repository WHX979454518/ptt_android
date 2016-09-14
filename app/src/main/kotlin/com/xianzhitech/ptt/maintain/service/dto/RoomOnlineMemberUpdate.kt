package com.xianzhitech.ptt.maintain.service.dto


interface RoomOnlineMemberUpdate {
    val roomId: String
    val memberIds: Collection<String>
}