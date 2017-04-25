package com.xianzhitech.ptt.ui.util

import android.content.Context
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet


class ImageView @JvmOverloads constructor(context: Context,
                                          attributeSet: AttributeSet? = null,
                                          defStyle: Int = 0) : AppCompatImageView(context, attributeSet, defStyle) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (scaleType == ScaleType.FIT_XY && drawable != null &&
                drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {

            val widthMode = MeasureSpec.getMode(widthMeasureSpec)
            val width = MeasureSpec.getSize(widthMeasureSpec)

            val heightMode = MeasureSpec.getMode(heightMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)

            val ratio = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight

            when {
                widthMode == MeasureSpec.UNSPECIFIED &&
                        heightMode != MeasureSpec.UNSPECIFIED -> {
                    val adjustedWidth = height * ratio
                    setMeasuredDimension(adjustedWidth.toInt(), height)
                }

                widthMode != MeasureSpec.UNSPECIFIED &&
                        heightMode == MeasureSpec.UNSPECIFIED -> {
                    val adjustedHeight = width / ratio
                    setMeasuredDimension(width, adjustedHeight.toInt())
                }
            }
        }
        else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}