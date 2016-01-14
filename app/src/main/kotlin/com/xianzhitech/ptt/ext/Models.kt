package com.xianzhitech.ptt.ext

import android.content.Context
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.model.ContactItem
import com.xianzhitech.ptt.ui.widget.TextDrawable
import kotlin.text.substring

/**
 * Created by fanchao on 14/01/16.
 */

fun ContactItem.getTintColor(context: Context) = context.resources.getIntArray(R.array.account_colors).let {
    it[hashCode() % it.size]
}

fun ContactItem.getIcon(context: Context) = TextDrawable(context, name.substring(0, 1), getTintColor(context))