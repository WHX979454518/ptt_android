package com.xianzhitech.ptt.maintain.service

import android.content.Context
import android.support.annotation.StringRes
import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.toFormattedString
import io.socket.client.SocketIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 *
 * 服务相关的异常
 *
 * Created by fanchao on 17/12/15.
 */

interface UserDescribableException {
    fun describe(context: Context): CharSequence
}

open class StaticUserException : UserDescribableException, RuntimeException {
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

object UnknownServerException : RuntimeException("Unknown server exception"), UserDescribableException {
    override fun describe(context: Context): CharSequence {
        return R.string.error_unknown_server.toFormattedString(context)
    }
}

class KnownServerException(val errorName: String, val errorMessage: String? = null) : RuntimeException(errorName), UserDescribableException {
    var errorMessageResolved: String? = null

    override fun describe(context: Context): CharSequence {
        if (errorMessage.isNullOrBlank().not()) {
            return errorMessage!!
        }

        if (errorMessageResolved?.isNotBlank() ?: false) {
            return errorMessageResolved!!
        }

        errorMessageResolved = context.getString(context.resources.getIdentifier("error_$errorName", "string", context.packageName)) ?: ""
        if (errorMessageResolved!!.isBlank()) {
            if (BuildConfig.DEBUG && errorName.isNotBlank()) {
                return errorName
            } else {
                return context.getString(R.string.error_unknown)
            }
        }

        return errorMessageResolved!!
    }
}

class ConnectivityException() : StaticUserException(R.string.error_unable_to_connect)

fun Throwable?.describeInHumanMessage(context: Context): CharSequence {
    return when {
        this is UserDescribableException -> describe(context)
        this is SocketTimeoutException ||
                (this is SocketIOException && this.message == "timeout") ||
                this is TimeoutException -> R.string.error_timeout.toFormattedString(context)
        else -> {
            if (BuildConfig.DEBUG && this != null) {
                this.message ?: R.string.error_unknown.toFormattedString(context)
            } else {
                R.string.error_unknown.toFormattedString(context)
            }
        }
    }
}