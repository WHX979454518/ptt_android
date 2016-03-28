package com.xianzhitech.ptt.util

import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.User
import java.text.Collator
import java.util.*

/**
 * Created by fanchao on 15/12/15.
 */
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
