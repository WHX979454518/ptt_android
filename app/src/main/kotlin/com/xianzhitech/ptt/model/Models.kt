package com.xianzhitech.ptt.model

import android.content.ContentValues
import android.database.Cursor
import android.support.annotation.ColorInt

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

