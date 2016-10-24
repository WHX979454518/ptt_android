package com.xianzhitech.ptt.model

import android.content.Context
import com.xianzhitech.ptt.R
import java.io.Serializable
import java.util.*
import java.util.regex.Pattern

data class DownTime(val startTime: LocalTime,
                    val endTime : LocalTime) : Serializable {

    fun isDownTime(now : Long) : Boolean {
        val currTime = LocalTime.fromCalendar(Calendar.getInstance().apply { timeInMillis = now })

        if (startTime > endTime) {
            return currTime >= startTime && currTime <= endTime
        }
        else {
            // 跨天的时间段
            return currTime >= startTime || currTime <= endTime
        }
    }

    override fun toString() : String {
        return "$startTime-$endTime"
    }

    fun toString(context : Context) : String {
        if (startTime < endTime) {
            return context.getString(R.string.downtime_same_day, startTime.toString(), endTime.toString())
        }
        else {
            return context.getString(R.string.downtime_cross_day, startTime.toString(), endTime.toString())
        }
    }

    companion object {
        private val pattern = Pattern.compile("(.+?)\\-(.+?)")

        fun fromString(str : String?) : DownTime? {
            if (str == null) {
                return null
            }

            val matcher = pattern.matcher(str)
            if (matcher.matches()) {
                return DownTime(startTime = LocalTime.fromString(matcher.group(1))!!, endTime = LocalTime.fromString(matcher.group(2))!!)
            }

            return null
        }
    }
}