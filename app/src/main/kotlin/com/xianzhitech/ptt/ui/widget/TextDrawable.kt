package com.xianzhitech.ptt.ui.widget

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.TextPaint
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.toDimen
import kotlin.collections.contains

/**
 * Created by fanchao on 30/12/15.
 */
class TextDrawable(context: Context, private val text: String, private val tintColor: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val textBounds = Rect()
    private var enabled = true
    private var selected = false

    init {
        paint.color = tintColor
        paint.style = Paint.Style.FILL
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        strokePaint.style = Paint.Style.STROKE
        strokePaint.color = Color.YELLOW
        strokePaint.strokeWidth = R.dimen.member_highlight_stroke.toDimen(context)
    }

    override fun onBoundsChange(bounds: Rect?) {
        bounds?.apply {
            textPaint.textSize = height().toFloat() * 0.5f
            textPaint.getTextBounds(text, 0, text.length, textBounds)
        }
        invalidateSelf()
    }

    override fun isStateful() = true

    override fun onStateChange(states: IntArray?): Boolean {
        return states?.let {
            var changed = false
            if (enabled != it.contains(android.R.attr.state_enabled)) {
                enabled = enabled.not()
                paint.color = if (enabled) tintColor else Color.GRAY
                changed = true
            }

            if (selected != it.contains(android.R.attr.state_selected)) {
                selected = selected.not()
                changed = true
            }

            changed
        } ?: false
    }

    override fun draw(canvas: Canvas?) {
        canvas?.apply {
            val halfWidth = width / 2f
            val halfHeight = height / 2f
            val radius = Math.min(halfHeight, halfWidth) - strokePaint.strokeWidth / 2f
            drawCircle(halfWidth, halfHeight, radius, paint)

            if (selected) {
                drawCircle(halfWidth, halfHeight, radius, strokePaint)
            }

            drawText(text, halfWidth, halfHeight - textBounds.exactCenterY(), textPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun getOpacity() = 0

    override fun setColorFilter(colorFilter: ColorFilter?) {
    }
}