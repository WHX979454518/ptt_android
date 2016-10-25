package com.xianzhitech.ptt.model

import java.io.Serializable
import java.util.*
import java.util.regex.Pattern


data class LocalTime(val minute : Int, val hourOfDay: Int) : Comparable<LocalTime>, Serializable {
    init {
        if (minute < 0 || minute > 59 || hourOfDay > 23 || hourOfDay < 0) {
            throw IllegalArgumentException()
        }
    }


    private val minutesOfDay : Int
    get() = hourOfDay * 60 + minute

    override fun toString() : String {
        return String.format(Locale.ENGLISH, "%d:%02d", hourOfDay, minute)
    }

    override fun compareTo(other: LocalTime): Int {
        return minutesOfDay - other.minutesOfDay
    }

    companion object {
        private val MATCHER = Pattern.compile("(\\d+):(\\d{2})")

        fun fromString(str : String?) : LocalTime? {
            val matcher = MATCHER.matcher(str ?: return null)
            if (matcher.matches()) {
                return LocalTime(minute = matcher.group(2).toInt(), hourOfDay = matcher.group(1).toInt())
            }

            return null
        }

        fun fromCalendar(calendar: Calendar) : LocalTime {
            return LocalTime(minute = calendar.get(Calendar.MINUTE), hourOfDay = calendar.get(Calendar.HOUR_OF_DAY))
        }

        fun isDownTime(now : Long, startTime: LocalTime, endTime: LocalTime) : Boolean {
            val currTime = LocalTime.fromCalendar(Calendar.getInstance().apply { timeInMillis = now })

            if (startTime > endTime) {
                return currTime >= startTime && currTime <= endTime
            }
            else {
                // 跨天的时间段
                return currTime >= startTime || currTime <= endTime
            }
        }

    }
}