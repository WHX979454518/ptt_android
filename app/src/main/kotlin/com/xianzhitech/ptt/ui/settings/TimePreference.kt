package com.xianzhitech.ptt.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.support.v7.preference.DialogPreference
import android.support.v7.preference.Preference
import android.util.AttributeSet
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.AppPreference
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import org.threeten.bp.LocalTime


class TimePreference @JvmOverloads constructor(context : Context,
                                               attrs : AttributeSet? = null,
                                               defStyleResAttr : Int = R.attr.preferenceStyle,
                                               defStyleRes : Int = R.style.Preference_DialogPreference) : DialogPreference(context, attrs, defStyleResAttr, defStyleRes) {

    private val isStart : Boolean
    private val startKey = context.getString(R.string.key_downtime_start)
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { pref, s ->
        if (s == startKey) {
            applyValue()
        }
    }

    val value : LocalTime
    get() = LocalTime.parse(getPersistedString(null), Constants.TIME_FORMAT)
            ?: (if (isStart) AppPreference.DEFAULT_DOWNTIME_START else AppPreference.DEFAULT_DOWNTIME_END)

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.TimePreference, defStyleResAttr, defStyleRes)
        isStart = typedArray.getBoolean(R.styleable.TimePreference_isStart, true)
        typedArray.recycle()
    }

    override fun onAttached() {
        super.onAttached()

        if (isStart.not()) {
            sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
            applyValue()
        }
    }

    override fun onDetached() {
        super.onDetached()

        if (isStart.not()) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        }
    }

    override fun onDependencyChanged(dependency: Preference?, disableDependent: Boolean) {
        super.onDependencyChanged(dependency, disableDependent)
        applyValue()
    }

    private fun applyValue() {
        val preference = (context.applicationContext as AppComponent).preference
        isVisible = preference.enableDownTime
        val v = value
        if (isStart.not()) {
            val startTime = LocalTime.parse(sharedPreferences.getString(startKey, null), Constants.TIME_FORMAT) ?: AppPreference.DEFAULT_DOWNTIME_START
            if (startTime >= v) {
                summary = context.getString(R.string.downtime_next_day_with_time, v.toString())
            }
            else {
                summary = v.toString()
            }
        }
        else {
            summary = v.toString()
        }
    }

    override public fun notifyChanged() {
        super.notifyChanged()
        applyValue()
    }
}