package com.xianzhitech.ptt.service.dto

import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.User


interface Contacts {
    val users: Collection<User>
    val groups: Collection<Group>
}