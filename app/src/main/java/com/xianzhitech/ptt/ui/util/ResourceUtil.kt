package com.xianzhitech.ptt.ui.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.v4.graphics.drawable.DrawableCompat

/**
 * Created by fanchao on 7/12/15.
 */
object ResourceUtil {

    fun getDrawable(context: Context, @DrawableRes drawable: Int): Drawable {
        if (Build.VERSION.SDK_INT >= 21) {
            return context.getDrawable(drawable)
        }
        return context.resources.getDrawable(drawable)
    }

    fun getTintedDrawable(context: Context, @DrawableRes drawableRes: Int, @ColorInt tintColor: Int): Drawable {
        val wrappedDrawable = DrawableCompat.wrap(getDrawable(context, drawableRes))
        DrawableCompat.setTint(wrappedDrawable, tintColor)
        return wrappedDrawable
    }

    fun getTintedDrawableWithColorRes(context: Context, @DrawableRes drawableRes: Int, @ColorRes tintColor: Int): Drawable {
        return getTintedDrawable(context, drawableRes, getColor(context, tintColor))
    }

    fun getColor(context: Context, @ColorRes colorRes: Int): Int {
        if (Build.VERSION.SDK_INT >= 23) {
            context.getColor(colorRes)
        }

        return context.resources.getColor(colorRes)
    }
}
