package com.xianzhitech.ptt.util

import com.xianzhitech.ptt.model.ContactItem
import com.xianzhitech.ptt.model.Group
import java.text.Collator
import java.util.*

/**
 * Created by fanchao on 15/12/15.
 */
class ContactComparator : Comparator<ContactItem> {
    private val collator: Collator

    constructor() {
        collator = Collator.getInstance()
    }

    constructor(locale: Locale) {
        collator = Collator.getInstance(locale)
    }

    override fun compare(lhs: ContactItem, rhs: ContactItem): Int {
        val lhsIsGroup = lhs is Group
        val rhsIsGroup = rhs is Group
        if (lhsIsGroup && rhsIsGroup || (!lhsIsGroup && !rhsIsGroup)) {
            return collator.compare(lhs.name, rhs.name)
        } else if (lhsIsGroup) {
            return -1
        } else {
            // if rhsIsGroups
            return 1
        }
    }
}
