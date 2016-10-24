package com.xianzhitech.ptt.ui.settings

import android.os.Bundle
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.Shortcut
import com.xianzhitech.ptt.ShortcutMode
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.model.DownTime
import com.xianzhitech.ptt.util.showDialogOnce
import rx.Single
import rx.subscriptions.CompositeSubscription

class SettingsFragment : PreferenceFragmentCompat(), DownTimePickerDialogFragment.OnTimeSetListener {
    private lateinit var modeNoOp : CharSequence
    private lateinit var modeFromContact : CharSequence
    private lateinit var modeFromRoom : CharSequence

//    private lateinit var shortcutPref: ListPreference
    private var shortcutSubscription : CompositeSubscription? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        modeNoOp = R.string.shortcut_mode_no_op.toFormattedString(activity)
        modeFromContact = R.string.shortcut_mode_contact.toFormattedString(activity)
        modeFromRoom = R.string.shortcut_mode_room.toFormattedString(activity)

        super.onCreate(savedInstanceState)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is DownTimePreference) {
            childFragmentManager.showDialogOnce(preference.key, {
                DownTimePickerDialogFragment.createInstance(
                        appComponent.preference.downTime,
                        preference.dialogTitle)
            })
        }
        else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onTimeSet(downTime: DownTime) {
        (context.applicationContext as AppComponent).preference.downTime = downTime
        (findPreference(getString(R.string.key_downtime)) as DownTimePreference).notifyChanged()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

//        shortcutPref = findPreference(getString(R.string.key_shortcut_mode)) as ListPreference
//        shortcutPref.entries = arrayOf(modeNoOp, modeFromContact, modeFromRoom)
//        shortcutPref.entryValues = shortcutPref.entries
//        shortcutPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, any ->
//            when (any) {
//                modeNoOp -> {
//                    val shortcut = Shortcut(ShortcutMode.NO_OP)
//                    appComponent.preference.shortcut = shortcut
//                    applyShortcut(shortcut)
//                }
//                modeFromContact -> {
//
//                }
//            }
//            false
//        }
    }

    override fun onStart() {
        super.onStart()

        shortcutSubscription = CompositeSubscription()
        applyShortcut(appComponent.preference.shortcut)
    }

    override fun onStop() {
        shortcutSubscription?.unsubscribe()
        shortcutSubscription = null

        super.onStop()
    }

    private fun applyShortcut(shortcut: Shortcut) {
//        shortcutSubscription?.apply {
//            add(shortcut.calleeName
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribeSimple {
//                    if (it == null) {
//                        shortcutPref.summary = R.string.shortcut_mode_no_op.toFormattedString(activity)
//                    } else {
//                        shortcutPref.summary = R.string.shortcut_with_callee.toFormattedString(activity, it)
//                    }
//                })
//        }
    }

    private val Shortcut.calleeName : Single<CharSequence?>
        get() = when (mode) {
            ShortcutMode.NO_OP -> Single.just(null)
            ShortcutMode.GROUP -> appComponent.groupRepository.getGroups(listOf(id)).getAsync()
                    .map { it.firstOrNull()?.name }
            ShortcutMode.USER -> appComponent.userRepository.getUser(id).getAsync()
                    .map { it?.name }
            ShortcutMode.ROOM -> appComponent.roomRepository.getRoomName(id, excludeUserIds = arrayOf(appComponent.signalHandler.peekCurrentUserId)).getAsync()
                    .map { it?.name  }
        }
}