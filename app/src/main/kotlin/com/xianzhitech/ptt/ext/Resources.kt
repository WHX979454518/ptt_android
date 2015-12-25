package com.xianzhitech.ptt.ext

import android.content.Context
import android.os.Build
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.v4.graphics.drawable.DrawableCompat

/**
 *
 * 资源帮助类
 *
 * Created by fanchao on 25/12/15.
 */

fun Context.getDrawableCompat(@DrawableRes drawable: Int) =
        if (Build.VERSION.SDK_INT >= 21) getDrawable(drawable)
        else resources.getDrawable(drawable)

fun Context.getTintedDrawable(@DrawableRes drawableRes: Int, @ColorInt tintColor: Int) =
        DrawableCompat.wrap(getDrawableCompat(drawableRes)).apply {
            DrawableCompat.setTint(this, tintColor)
        }

fun Context.getColorCompat(@ColorRes colorRes: Int) =
        if (Build.VERSION.SDK_INT >= 23) getColor(colorRes)
        else resources.getColor(colorRes)