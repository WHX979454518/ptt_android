package com.xianzhitech.ptt.data


data class RoomDetails(val room: Room,
                       val name: String,
                       val member: List<User>)