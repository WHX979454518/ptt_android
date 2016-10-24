package com.xianzhitech.ptt.ui.settings

import android.content.Context
import android.support.v7.preference.DialogPreference
import android.support.v7.preference.Preference
import android.util.AttributeSet
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R


class DownTimePreference @JvmOverloads constructor(context : Context,
                                                   attrs : AttributeSet? = null,
                                                   defStyleResAttr : Int = 0,
                                                   defStyleRes : Int = 0) : DialogPreference(context, attrs, defStyleResAttr, defStyleRes) {


    private val startTimePreferenceKey: String?

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DownTimePreference, defStyleResAttr, defStyleRes)
        startTimePreferenceKey = typedArray.getString(R.styleable.DownTimePreference_startTimePreference)
        if (startTimePreferenceKey != null) {
            dependency = startTimePreferenceKey
        }
        typedArray.recycle()
    }

    override fun onDependencyChanged(dependency: Preference?, disableDependent: Boolean) {
        super.onDependencyChanged(dependency, disableDependent)
        applyValue()
    }

    private fun applyValue() {
        val preference = (context.applicationContext as AppComponent).preference
        isEnabled = preference.enableDownTime
        summary = preference.downTime.toString(context)
    }

    override public fun notifyChanged() {
        super.notifyChanged()
        applyValue()
    }
}