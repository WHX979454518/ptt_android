package com.xianzhitech.ptt.service

import android.content.Context
import android.support.annotation.StringRes
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.toFormattedString

/**
 *
 * 服务相关的异常
 *
 * Created by fanchao on 17/12/15.
 */

interface UserDescribableException {
    fun describe(context: Context): CharSequence
}

class StaticUserException : UserDescribableException, RuntimeException {
    private val msg: Any
    private val args: Array<out Any?>

    constructor(@StringRes stringRes: Int, vararg args: Any?) {
        msg = stringRes
        this.args = args
    }

    constructor(msg: String) : super(msg) {
        this.msg = msg
        this.args = arrayOf()
    }

    override fun describe(context: Context): CharSequence {
        return when (msg) {
            is Int -> msg.toFormattedString(context, *args)
            else -> msg.toString()
        }
    }
}

class UserNotLogonException : RuntimeException(), UserDescribableException {
    override fun describe(context: Context) = R.string.error_user_not_logon.toFormattedString(context)
}


class ServerException(val serverMsg: String) : RuntimeException(serverMsg), UserDescribableException {
    override fun describe(context: Context) = serverMsg
}

class InvalidSavedTokenException : RuntimeException()