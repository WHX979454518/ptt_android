package com.podkitsoftware.shoumi.model;

import java.util.List;

public class GroupInfo<T> {
    public final Group group;
    public final List<T> members;
    public final int memberCount;

    public GroupInfo(final Group group, final List<T> members, final int memberCount) {
        this.group = group;
        this.members = members;
        this.memberCount = memberCount;
    }
}
