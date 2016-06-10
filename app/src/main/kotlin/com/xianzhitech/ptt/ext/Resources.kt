package com.xianzhitech.ptt.ext

import android.content.Context
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.text.format.DateFormat
import android.text.format.DateUtils
import com.xianzhitech.ptt.R
import java.util.*

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

object DateConstants {
    const val ONE_HOUR_MILLS = 3600 * 1000L
    const val ONE_DAY_MILLS = 24 * ONE_HOUR_MILLS
}


fun Date.formatInvite(context: Context): CharSequence {
    if (DateUtils.isToday(time)) {
        return DateFormat.format("HH:mm", this)
    } else {
        val distance = time - System.currentTimeMillis()
        if (distance < DateConstants.ONE_DAY_MILLS) {
            return R.string.yesterday.toFormattedString(context)
        } else {
            val days = (distance / DateConstants.ONE_DAY_MILLS).toInt()
            return context.resources.getQuantityString(R.plurals.days_ago, days, days)
        }
    }
}