package com.xianzhitech.ptt.model

enum class Permission {
    MAKE_INDIVIDUAL_CALL,
    MAKE_GROUP_CALL,
    RECEIVE_INDIVIDUAL_CALL,
    RECEIVE_ROOM,
    SPEAK,
    MUTE, // 免打扰
    FORCE_INVITE, // 强制拉人
    ;
}