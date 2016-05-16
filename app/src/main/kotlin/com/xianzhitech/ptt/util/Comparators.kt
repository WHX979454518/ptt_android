package com.xianzhitech.ptt.util

import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.ExtraRoomInfo
import java.text.Collator
import java.util.*

object RoomComparator : Comparator<Room> {

    override fun compare(p0: Room, p1: Room): Int {
        if (p0.id == p1.id) {
            return 0
        }

        if (p0 is ExtraRoomInfo && p1 is ExtraRoomInfo) {
            val rc = p0.lastActiveTime.timeOrZero().compareTo(p1.lastActiveTime.timeOrZero())
            if (rc != 0) {
                return -rc
            }
        }

        return p0.id.compareTo(p1.id)
    }

    private fun Date?.timeOrZero() = this?.time ?: 0
}


class ContactComparator : Comparator<Any> {
    private val collator: Collator

    constructor() {
        collator = Collator.getInstance()
    }

    constructor(locale: Locale) {
        collator = Collator.getInstance(locale)
    }

    override fun compare(lhs: Any, rhs: Any): Int {
        val lhsIsGroup = lhs is Group
        val rhsIsGroup = rhs is Group
        val lhsName = if (lhsIsGroup) (lhs as Group).name else (lhs as User).name
        val rhsName = if (rhsIsGroup) (rhs as Group).name else (rhs as User).name
        if (lhsIsGroup && rhsIsGroup || (!lhsIsGroup && !rhsIsGroup)) {
            return collator.compare(lhsName, rhsName)
        } else if (lhsIsGroup) {
            return -1
        } else {
            // if rhsIsGroups
            return 1
        }
    }
}
