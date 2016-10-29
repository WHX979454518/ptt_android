package com.xianzhitech.ptt.model

enum class Permission {
    MAKE_INDIVIDUAL_CALL,
    MAKE_TEMPORARY_GROUP_CALL,
    RECEIVE_INDIVIDUAL_CALL,
    RECEIVE_TEMPORARY_GROUP_CALL,
    SPEAK,
    MUTE, // 免打扰
    FORCE_INVITE, // 强制拉人
    ;
}