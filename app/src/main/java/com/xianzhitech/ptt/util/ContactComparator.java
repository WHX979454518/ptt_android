package com.xianzhitech.ptt.util;

import com.xianzhitech.ptt.model.ContactItem;
import com.xianzhitech.ptt.model.Group;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * Created by fanchao on 15/12/15.
 */
public class ContactComparator implements Comparator<ContactItem> {
    private final Collator collator;

    public ContactComparator() {
        collator = Collator.getInstance();
    }

    public ContactComparator(final Locale locale) {
        collator = Collator.getInstance(locale);
    }

    @Override
    public int compare(final ContactItem lhs, final ContactItem rhs) {
        final boolean lhsIsGroup = lhs instanceof Group, rhsIsGroup = rhs instanceof Group;
        if (lhsIsGroup && rhsIsGroup || (!lhsIsGroup && !rhsIsGroup)) {
            return collator.compare(lhs.getName(), rhs.getName());
        }
        else if (lhsIsGroup) {
            return -1;
        }
        else { // if rhsIsGroups
            return 1;
        }
    }
}
