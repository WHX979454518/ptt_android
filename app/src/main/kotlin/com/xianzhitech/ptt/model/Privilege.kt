package com.xianzhitech.ptt.model

import java.util.*

enum class Privilege {
    MAKE_CALL,
    CREATE_ROOM,
    RECEIVE_CALL,
    RECEIVE_ROOM;
}

fun String?.toPrivileges(): EnumSet<Privilege> {
    val result = EnumSet.noneOf(Privilege::class.java)
    this?.foldIndexed(0, { index, lastIndex, c ->
        if (c == '|') {
            result += Privilege.valueOf(substring(lastIndex, index))
            index + 1
        }
        else {
            lastIndex
        }
    })
    return result
}

fun Iterable<Privilege>?.toDatabaseString() = this?.joinToString(separator = "|")