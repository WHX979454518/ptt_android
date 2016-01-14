package com.xianzhitech.ptt.model

import java.util.*
import kotlin.collections.forEach
import kotlin.collections.joinToString
import kotlin.collections.plusAssign
import kotlin.text.split

enum class Privilege {
    MAKE_CALL,
    CREATE_ROOM,
    RECEIVE_CALL,
    RECEIVE_ROOM;
}

fun String?.toPrivileges(): EnumSet<Privilege> {
    val result = EnumSet.noneOf(Privilege::class.java)
    this?.split('|')?.forEach {
        result += Privilege.valueOf(it)
    }
    return result
}

fun Iterable<Privilege>?.toDatabaseString() = this?.joinToString(separator = "|")