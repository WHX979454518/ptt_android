package com.xianzhitech.ptt.service

import android.content.Context
import android.support.annotation.StringRes
import android.widget.Toast
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.xianzhitech.ptt.App
import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.Permission
import com.xianzhitech.ptt.ext.toFormattedString
import io.socket.client.SocketIOException
import io.socket.engineio.client.EngineIOException
import retrofit2.HttpException
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
    private val args: List<Any?>

    constructor(@StringRes stringRes: Int, vararg args: Any?) {
        msg = stringRes
        this.args = args.toList()
    }

    constructor(msg: String) : super(msg) {
        this.msg = msg
        this.args = emptyList()
    }

    override fun describe(context: Context): CharSequence {
        return when (msg) {
            is Int -> msg.toFormattedString(context, *args.toTypedArray())
            else -> msg.toString()
        }
    }
}

fun Throwable?.describeInHumanMessage(context: Context): CharSequence {
    return when {
        this is UserDescribableException -> describe(context)
        this is HttpException || this is EngineIOException -> R.string.error_unable_to_connect.toFormattedString(context)
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

fun Throwable?.toast() {
    Toast.makeText(App.instance, describeInHumanMessage(App.instance), Toast.LENGTH_LONG).show()
}

data class NoPermissionException(val permission : Permission?) : RuntimeException("No permission $permission"), UserDescribableException {
    override fun describe(context: Context): CharSequence {
        return context.getString(R.string.error_no_permission)
    }
}

data class NoSuchRoomException(val roomId: String?) : RuntimeException(), UserDescribableException {
    override fun describe(context: Context): CharSequence {
        return context.getString(R.string.error_no_such_room)
    }
}

data class ServerException @JsonCreator constructor(@param:JsonProperty("name") val name: String?,
                                                    @param:JsonProperty("message") override val message: String?) : RuntimeException(name), UserDescribableException {
    var errorMessageResolved: String? = null

    override fun describe(context: Context): CharSequence {
        if (message.isNullOrBlank().not()) {
            return message!!
        }

        if (errorMessageResolved?.isNotBlank() ?: false) {
            return errorMessageResolved!!
        }

        errorMessageResolved = context.getString(context.resources.getIdentifier("error_$name", "string", context.packageName)) ?: ""
        if (errorMessageResolved!!.isBlank()) {
            if (BuildConfig.DEBUG && name?.isNotBlank() ?: false) {
                return name!!
            } else {
                return context.getString(R.string.error_unknown)
            }
        }

        return errorMessageResolved!!
    }
}