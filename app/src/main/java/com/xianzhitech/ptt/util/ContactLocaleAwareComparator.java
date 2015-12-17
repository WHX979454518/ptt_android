package com.xianzhitech.ptt.util;

import com.xianzhitech.ptt.model.ContactItem;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * Created by fanchao on 15/12/15.
 */
public class ContactLocaleAwareComparator implements Comparator<ContactItem> {
    private final Collator collator;

    public ContactLocaleAwareComparator() {
        collator = Collator.getInstance();
    }

    public ContactLocaleAwareComparator(final Locale locale) {
        collator = Collator.getInstance(locale);
    }

    @Override
    public int compare(final ContactItem lhs, final ContactItem rhs) {
        return collator.compare(lhs.getName(), rhs.getName());
    }
}
