package com.zrt.ptt.app.console.mvp.bean;

import com.xianzhitech.ptt.data.ContactEnterprise;
import com.xianzhitech.ptt.data.ContactGroup;

import java.util.List;
import java.util.Set;

import io.reactivex.annotations.NonNull;

/**
 * Created by surpass on 2017-5-15.
 */

public class OrganizationBean {
    private ContactEnterprise contactEnterprise;
    private Set<String> strings;
    private List<ContactGroup> contactGroups;

    public OrganizationBean(ContactEnterprise contactEnterprise, Set<String> strings, List<ContactGroup> contactGroups) {
        this.contactEnterprise = contactEnterprise;
        this.strings = strings;
        this.contactGroups = contactGroups;
    }

    public ContactEnterprise getContactEnterprise() {
        return contactEnterprise;
    }

    public void setContactEnterprise(ContactEnterprise contactEnterprise) {
        this.contactEnterprise = contactEnterprise;
    }

    public Set<String> getStrings() {
        return strings;
    }

    public void setStrings(Set<String> strings) {
        this.strings = strings;
    }

    public List<ContactGroup> getContactGroups() {
        return contactGroups;
    }

    public void setContactGroups(List<ContactGroup> contactGroups) {
        this.contactGroups = contactGroups;
    }
}
