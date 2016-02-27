package com.xianzhitech.ptt.ext

import android.content.Context
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.repo.RoomWithMembers

/**
 *
 * 资源帮助类
 *
 * Created by fanchao on 25/12/15.
 */

fun Context.getTintedDrawable(@DrawableRes drawableRes: Int, @ColorInt tintColor: Int) =
        DrawableCompat.wrap(getDrawableCompat(drawableRes)).apply {
            DrawableCompat.setTint(this, tintColor)
        }

fun Context.getColorCompat(@ColorRes colorRes: Int) = ContextCompat.getColor(this, colorRes)
fun Context.getDrawableCompat(@DrawableRes drawableRes: Int) = ContextCompat.getDrawable(this, drawableRes)

fun Int.toColorValue(context: Context) = context.getColorCompat(this)
fun Int.toDimen(context: Context) = context.resources.getDimension(this)
fun Int.toFormattedString(context: Context, vararg args: Any?) = context.getString(this, *args)

private fun List<String>.getMemberNames(context: Context, totalNumber : Int, maxDisplaySize : Int) : String {
    val displaySize = Math.min(maxDisplaySize, size)
    val separator = R.string.member_separator.toFormattedString(context)
    return if (displaySize < totalNumber) {
        return R.string.group_member_with_more.toFormattedString(context, subList(0, displaySize).joinToString(separator = separator))
    }
    else {
        R.string.group_member.toFormattedString(context, joinToString(separator = separator))
    }
}

fun RoomWithMembers.getMemberNames(context: Context, maxDisplaySize: Int = 3) : String {
    return members.map { it.name }.getMemberNames(context, memberCount, maxDisplaySize)
}


fun RoomWithMembers.getRoomName(context: Context) =
        if (room.name.isNullOrBlank()) getMemberNames(context)
        else room.name