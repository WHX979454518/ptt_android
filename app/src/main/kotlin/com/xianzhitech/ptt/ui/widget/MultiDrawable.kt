package com.xianzhitech.ptt.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.support.annotation.ColorInt
import android.support.v4.content.ContextCompat
import com.xianzhitech.ptt.R

class MultiDrawable(val context: Context,
                    children : List<Drawable> = emptyList(),
                    @ColorInt val backgroundColor : Int = ContextCompat.getColor(context, R.color.grey_500),
                    val gridSpacerSize: Int = context.resources.getDimensionPixelSize(R.dimen.divider_normal),
                    val columnCount: Int = 3) : Drawable() {

    var children : List<Drawable> = children
    set(value) {
        if (field != value) {
            field.forEach {
                if (it.callback == childrenCallbacks) {
                    it.callback = null
                }
            }

            field = value
            value.forEach { it.callback = childrenCallbacks }
            invalidateSelf()
        }
    }

    private var selfAlpha : Int = 255
    private var selfColorFilter : ColorFilter? = null

    private val childrenCallbacks = object : Drawable.Callback {
        override fun unscheduleDrawable(drawable: Drawable?, runnable: Runnable?) {
            callback?.unscheduleDrawable(this@MultiDrawable, runnable)
        }

        override fun invalidateDrawable(drawable: Drawable?) {
            callback?.invalidateDrawable(drawable)
        }

        override fun scheduleDrawable(drawable: Drawable?, runnable: Runnable?, delay: Long) {
            callback?.scheduleDrawable(drawable, runnable, delay)
        }
    }

    init {
        if (columnCount < 1) {
            throw IllegalArgumentException("Column count must be no less than 1, currently it's $columnCount")
        }

        if (gridSpacerSize < 0) {
            throw IllegalArgumentException("Grid space must be no less than 0, currently it's $gridSpacerSize")
        }
    }


    override fun draw(canvas: Canvas) {
        val width = bounds.width()
        val height = bounds.height()
        if (width <= 0 || height <= 0) {
            return
        }

        // Draw background
        canvas.drawColor(backgroundColor)

        if (children.isNotEmpty()) {
            var childSize = Math.floor((width - (columnCount - 1 + 2) * gridSpacerSize) / columnCount.toDouble()).toInt()
            val padding = (width - childSize * columnCount - gridSpacerSize * (columnCount - 1)) / 2

            val rowCount = Math.ceil(children.size / columnCount.toDouble()).toInt()
            if (rowCount > 1) {
                val firstRowColumnCount = (children.size % columnCount).let {
                    if (it == 0) columnCount
                    else it
                }

                // Draw first row
                val firstRowLeftPadding =
                        if (firstRowColumnCount != columnCount) (width - childSize * firstRowColumnCount - gridSpacerSize * (firstRowColumnCount - 1)) / 2 else padding

                drawRow(firstRowLeftPadding, padding, childSize, canvas, 0, firstRowColumnCount)

                // Draw rest row
                for (i in 1..rowCount - 1) {
                    val rowStartIndex = (children.size - firstRowColumnCount) / rowCount * i
                    drawRow(padding, padding, childSize, canvas, rowStartIndex, columnCount)
                }
            }
            else {
                // Draw the only row from left
                drawRow(padding, padding, childSize, canvas, 0, children.size)
            }
        }
    }

    private fun drawRow(x : Int, y : Int, childSize : Int, canvas : Canvas, childStartIndex : Int, childCount : Int) {
        for (i in 0..childCount - 1) {
            var childX = x + i * childSize
            if (i > 0) {
                childX += (i - 1) * gridSpacerSize
            }
            drawChild(childX, y, childSize, canvas, children[childStartIndex + i])
        }
    }

    private fun drawChild(x : Int, y : Int, size : Int, canvas: Canvas, child : Drawable) {
        child.alpha = selfAlpha
        child.colorFilter = selfColorFilter
        child.setBounds(x, y, x + size, y + size)
        child.draw(canvas)
    }

    override fun onBoundsChange(bounds: Rect?) {
        super.onBoundsChange(bounds)

        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        if (selfAlpha != alpha) {
            selfAlpha = alpha
            invalidateSelf()
        }
    }

    override fun getOpacity() = PixelFormat.OPAQUE

    override fun setColorFilter(newColorFilter: ColorFilter?) {
        if (colorFilter != selfColorFilter) {
            colorFilter = selfColorFilter
            invalidateSelf()
        }
    }
}