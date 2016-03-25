package com.xianzhitech.ptt

/**
 * 提供程序选项数据的永久存储
 */
interface Preference {
    var userSessionToken: String?
    var lastLoginUserId: String?
    var blockCalls : Boolean
    var autoExit : Boolean
}