package com.xianzhitech.ptt.repo

import com.xianzhitech.ptt.model.Room
import java.util.*


interface RoomModel : Room {
    val lastSpeakTime: Date?
    val lastSpeakMemberId: String?
    val lastActiveTime : Date
}