package com.xianzhitech.ptt.util

import org.threeten.bp.LocalTime


fun LocalTime.isDownTime(startTime: LocalTime,
                         endTime: LocalTime) : Boolean {
    if (startTime < endTime) {
        return this >= startTime && this <= endTime
    }
    else {
        // 跨天的时间段
        return this >= startTime || this <= endTime
    }
}
