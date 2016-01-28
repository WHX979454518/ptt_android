package com.xianzhitech.ptt.ext

import android.content.Context
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.model.ContactItem
import com.xianzhitech.ptt.ui.widget.TextDrawable

/**
 * 获取该通讯录条目的主题颜色
 */
fun ContactItem.getTintColor(context: Context) = context.resources.getIntArray(R.array.account_colors).let {
    it[hashCode() % it.size]
}

/**
 * 获取该通讯录条目的图标
 */
fun ContactItem.getIcon(context: Context) = TextDrawable(context, name.substring(0, 1), getTintColor(context))