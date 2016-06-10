package com.xianzhitech.ptt.ui.widget

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

open class DrawableWrapper(drawable: Drawable? = null) : Drawable() {
    var drawable: Drawable? = drawable
        set(value) {
            if (field != value) {
                field?.let {
                    if (it.callback == childCallback) {
                        it.callback = null
                    }
                }

                field = value
                field?.let { it.callback = childCallback }
                invalidateSelf()
            }
        }
    private var selfAlpha: Int = 255
    private var selfColorFilter: ColorFilter? = null

    private val childCallback = object : Drawable.Callback {
        override fun unscheduleDrawable(drawable: Drawable?, runnable: Runnable?) {
            callback?.unscheduleDrawable(this@DrawableWrapper, runnable)
        }

        override fun invalidateDrawable(drawable: Drawable?) {
            callback?.invalidateDrawable(this@DrawableWrapper)
        }

        override fun scheduleDrawable(drawable: Drawable?, runnable: Runnable?, delay: Long) {
            callback?.scheduleDrawable(this@DrawableWrapper, runnable, delay)
        }
    }

    override fun getIntrinsicHeight() = drawable?.intrinsicHeight ?: -1
    override fun getIntrinsicWidth() = drawable?.intrinsicWidth ?: -1


    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) {
            return
        }

        drawable?.let {
            it.bounds = bounds
            it.colorFilter = selfColorFilter
            it.alpha = selfAlpha
            it.draw(canvas)
        }
    }

    override fun setAlpha(alpha: Int) {
        if (selfAlpha != alpha) {
            selfAlpha = alpha
            invalidateSelf()
        }
    }

    override fun getOpacity() = drawable?.opacity ?: PixelFormat.TRANSPARENT
    override fun setColorFilter(colorFilter: ColorFilter?) {
        if (selfColorFilter != colorFilter) {
            selfColorFilter = colorFilter
            invalidateSelf()
        }
    }
}