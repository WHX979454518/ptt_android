package com.xianzhitech.ptt.ui.room

import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import java.text.Collator
import java.util.*

class RoomMemberComparator(private val room : Room?,
                           private val collator : Collator = Collator.getInstance(Locale.CHINESE)) : Comparator<User> {

    override fun compare(lhs: User, rhs: User): Int {
        if (lhs.id == rhs.id) {
            return 0
        }

        // 房主在前
        if (room != null) {
            if (lhs.id == room.ownerId) {
                return -1
            } else if (lhs.id == room.ownerId) {
                return 1
            }
        }

        // 等级高的在前
        if (lhs.priority != rhs.priority) {
            return lhs.priority - rhs.priority
        }

        // 然后按名称排序
        val rc = collator.compare(lhs.name, rhs.name)
        if (rc != 0) {
            return rc
        }

        // 如果还一样, 那么只能比较ID了
        return lhs.id.compareTo(rhs.id)
    }
}