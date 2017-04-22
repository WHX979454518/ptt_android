package com.xianzhitech.ptt.ui.settings

import android.os.Bundle
import android.support.v7.preference.CheckBoxPreference
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.Permission
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.ui.dialog.TimePickerDialogFragment
import com.xianzhitech.ptt.util.showDialogOnce
import org.threeten.bp.LocalTime

class SettingsFragment : PreferenceFragmentCompat(), TimePickerDialogFragment.OnTimeSetListener {
    private lateinit var modeNoOp: CharSequence
    private lateinit var modeFromContact: CharSequence
    private lateinit var modeFromRoom: CharSequence

//    private lateinit var shortcutPref: ListPreference

    override fun onCreate(savedInstanceState: Bundle?) {
        modeNoOp = R.string.shortcut_mode_no_op.toFormattedString(activity)
        modeFromContact = R.string.shortcut_mode_contact.toFormattedString(activity)
        modeFromRoom = R.string.shortcut_mode_room.toFormattedString(activity)

        super.onCreate(savedInstanceState)

        (findPreference(getString(R.string.key_enable_downtime)) as CheckBoxPreference).apply {
            val hasPermission = appComponent.signalBroker.currentUser.value.orNull()?.hasPermission(Permission.MUTE) ?: false
            isEnabled = hasPermission
            isChecked = hasPermission && isChecked
            if (hasPermission.not()) {
                summary = getString(R.string.no_permission_to_downtime)
            } else {
                summary = getString(R.string.downtime_description)
            }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is TimePreference) {
            childFragmentManager.showDialogOnce(preference.key, {
                TimePickerDialogFragment.createInstance(preference.value)
            })
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onTimeSet(dialogFragment: TimePickerDialogFragment, time: LocalTime) {
        (findPreference(dialogFragment.tag) as TimePreference).apply {
            sharedPreferences.edit().putString(dialogFragment.tag, time.toString()).apply()
            notifyChanged()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)
    }
}