package com.xianzhitech.ptt.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.xianzhitech.ptt.R

class MaxHeightFrameLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {
    var maxHeight: Int = -1
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.MaxHeightFrameLayout, defStyleAttr, 0)
        maxHeight = typedArray.getDimensionPixelSize(R.styleable.MaxHeightFrameLayout_maxHeight, -1)
        typedArray.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightSpec = if (maxHeight >= 0) {
            MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
        } else {
            heightMeasureSpec
        }
        super.onMeasure(widthMeasureSpec, heightSpec)
    }
}