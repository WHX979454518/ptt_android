package com.xianzhitech.ptt.model

import java.util.*

enum class Permission {
    MAKE_INDIVIDUAL_CALL,
    MAKE_GROUP_CALL,
    CREATE_ROOM,
    RECEIVE_INDIVIDUAL_CALL,
    RECEIVE_ROOM;
}

fun String?.toPrivileges(): EnumSet<Permission> {
    val result = EnumSet.noneOf(Permission::class.java)
    this?.foldIndexed(0, { index, lastIndex, c ->
        if (c == '|') {
            result += Permission.valueOf(substring(lastIndex, index))
            index + 1
        }
        else {
            lastIndex
        }
    })
    return result
}

fun Iterable<Permission>?.toDatabaseString() = this?.joinToString(separator = "|")