package com.xianzhitech.ptt.model

import java.util.*

enum class Privilege {
    MAKE_CALL,
    CREATE_GROUP,
    RECEIVE_CALL,
    RECEIVE_GROUP;
}

fun String?.toPrivileges(): EnumSet<Privilege> {
    val result = EnumSet.noneOf(Privilege::class.java)
    this?.split('|')?.forEach {
        result += Privilege.valueOf(it)
    }
    return result
}

fun Iterable<Privilege>?.toDatabaseString() = this?.joinToString(separator = "|")

