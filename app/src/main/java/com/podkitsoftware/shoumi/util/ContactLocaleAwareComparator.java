package com.podkitsoftware.shoumi.util;

import com.podkitsoftware.shoumi.model.IContactItem;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * Created by fanchao on 15/12/15.
 */
public class ContactLocaleAwareComparator implements Comparator<IContactItem> {
    private final Collator collator;

    public ContactLocaleAwareComparator() {
        collator = Collator.getInstance();
    }

    public ContactLocaleAwareComparator(final Locale locale) {
        collator = Collator.getInstance(locale);
    }

    @Override
    public int compare(final IContactItem lhs, final IContactItem rhs) {
        return collator.compare(lhs.getName(), rhs.getName());
    }
}
