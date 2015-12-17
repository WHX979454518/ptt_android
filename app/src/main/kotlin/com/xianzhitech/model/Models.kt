package com.xianzhitech.model

import android.content.ContentValues
import android.database.Cursor
import android.support.annotation.ColorInt
import com.xianzhitech.ptt.model.Privilege
import org.json.JSONObject

/**
 * Created by fanchao on 17/12/15.
 */

interface Model {
    fun toValues(values: ContentValues)
    fun from(cursor: Cursor): Model
}

interface ContactItem {
    @ColorInt val tintColor: Int
    val name: CharSequence
    val avatar: String?
}

@Privilege
fun JSONObject?.toPrivilege(): Int {
    @Privilege var result = 0

    if (this == null) {
        return result
    }

    if (hasPrivilege("call")) {
        result = result or Privilege.MAKE_CALL
    }

    if (hasPrivilege("group")) {
        result = result or Privilege.CREATE_GROUP
    }

    if (hasPrivilege("recvCall")) {
        result = result or Privilege.RECEIVE_CALL
    }

    if (hasPrivilege("recvGroup")) {
        result = result or Privilege.RECEIVE_GROUP
    }

    return result
}

private fun JSONObject.hasPrivilege(name: String) = has(name) && getBoolean(name)

