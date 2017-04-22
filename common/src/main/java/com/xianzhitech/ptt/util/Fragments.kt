package com.xianzhitech.ptt.util

import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager


inline fun <reified T> Fragment.parentAs() : T {
    if (parentFragment is T) {
        return parentFragment as T
    }

    if (activity is T) {
        return activity as T
    }

    throw IllegalStateException("Parent is not implementing class ${T::class.simpleName}n")
}

inline fun FragmentManager.showDialogOnce(tag: String, dialogInit : () -> DialogFragment) {
    if (findFragmentByTag(tag) == null) {
        dialogInit().show(this, tag)
        executePendingTransactions()
    }
}