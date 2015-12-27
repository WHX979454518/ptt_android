package com.xianzhitech.ptt.service

/**
 *
 * 服务相关的异常
 *
 * Created by fanchao on 17/12/15.
 */

class ServerException(val serverMsg : String) : RuntimeException(serverMsg)

class InvalidSavedTokenException : RuntimeException()