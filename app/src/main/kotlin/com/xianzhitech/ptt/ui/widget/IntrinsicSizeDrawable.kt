package com.xianzhitech.ptt.ui.widget

import android.content.Context
import android.graphics.drawable.Drawable

class IntrinsicSizeDrawable(drawable : Drawable,
                            private val width : Int,
                            private val height : Int) : DrawableWrapper(drawable) {
    constructor(context: Context, drawable: Drawable, widthRes : Int, heightRes : Int)
        :this(drawable, context.resources.getDimensionPixelSize(widthRes), context.resources.getDimensionPixelSize(heightRes))

    override fun getIntrinsicHeight() = height
    override fun getIntrinsicWidth() = width
}