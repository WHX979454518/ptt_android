package com.xianzhitech.ptt.util

import android.content.Context
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.getRoomName
import java.text.Collator
import java.util.*

class RoomComparator(private val context: Context,
                     private val collator: Collator = Collator.getInstance()) : Comparator<Room> {


    override fun compare(p0: Room, p1: Room): Int {
        if (p0 == p1) {
            return 0
        }

        var rc : Int = p0.lastActiveTime.timeOrZero().compareTo(p1.lastActiveTime.timeOrZero())
        if (rc != 0) {
            return rc
        }

        rc = collator.compare(p0.getRoomName(context), p1.getRoomName(context))
        if (rc != 0) {
            return rc
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
