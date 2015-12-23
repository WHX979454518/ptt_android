package com.xianzhitech.ptt.ui.home

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog

/**

 * 提供一个警告对话框

 * Created by fanchao on 17/12/15.
 */
class AlertDialogFragment : DialogFragment(), DialogInterface.OnClickListener {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(context).setTitle(arguments.getCharSequence(ARG_TITLE)).setMessage(arguments.getCharSequence(ARG_MESSAGE)).setPositiveButton(arguments.getCharSequence(ARG_BTN_POSITIVE), this).setNegativeButton(arguments.getCharSequence(ARG_BTN_NEGATIVE), this).setNeutralButton(arguments.getCharSequence(ARG_BTN_NEUTRAL), this).create()
    }

    private fun <T> getParentAs(clazz: Class<T>): T? {
        if (clazz.isInstance(parentFragment)) {
            return parentFragment as T
        } else if (clazz.isInstance(activity)) {
            return activity as T
        } else {
            return null
        }
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        when (which) {
            DialogInterface.BUTTON_POSITIVE -> {
                val callback = getParentAs(OnPositiveButtonClickListener::class.java)
                callback?.onPositiveButtonClicked(this)
            }

            DialogInterface.BUTTON_NEGATIVE -> {
                val callback = getParentAs(OnNegativeButtonClickListener::class.java)
                callback?.onNegativeButtonClicked(this)
            }

            DialogInterface.BUTTON_NEUTRAL -> {
                val callback = getParentAs(OnNeutralButtonClickListener::class.java)
                callback?.onNeutralButtonClicked(this)
            }
        }
    }

    interface OnPositiveButtonClickListener {
        fun onPositiveButtonClicked(fragment: AlertDialogFragment)
    }

    interface OnNegativeButtonClickListener {
        fun onNegativeButtonClicked(fragment: AlertDialogFragment)
    }

    interface OnNeutralButtonClickListener {
        fun onNeutralButtonClicked(fragment: AlertDialogFragment)
    }

    class Builder {
        private var title: CharSequence? = null
        private var message: CharSequence? = null
        private var btnPositive: CharSequence? = null
        private var btnNegative: CharSequence? = null
        private var btnNeutral: CharSequence? = null

        fun setTitle(title: CharSequence): Builder {
            this.title = title
            return this
        }

        fun setMessage(message: CharSequence): Builder {
            this.message = message
            return this
        }

        fun setBtnPositive(btnPositive: CharSequence): Builder {
            this.btnPositive = btnPositive
            return this
        }

        fun setBtnNegative(btnNegative: CharSequence): Builder {
            this.btnNegative = btnNegative
            return this
        }

        fun setBtnNeutral(btnNeutral: CharSequence): Builder {
            this.btnNeutral = btnNeutral
            return this
        }

        fun create(): AlertDialogFragment {
            val fragment = AlertDialogFragment()
            val args = Bundle()
            args.putCharSequence(ARG_TITLE, title)
            args.putCharSequence(ARG_MESSAGE, message)
            args.putCharSequence(ARG_BTN_POSITIVE, btnPositive)
            args.putCharSequence(ARG_BTN_NEGATIVE, btnNegative)
            args.putCharSequence(ARG_BTN_NEUTRAL, btnNeutral)
            fragment.arguments = args
            return fragment
        }

        fun show(fragmentManager: FragmentManager, tag: String) {
            create().show(fragmentManager, tag)
        }

    }

    companion object {


        val ARG_TITLE = "arg_title"
        val ARG_MESSAGE = "arg_message"
        val ARG_BTN_POSITIVE = "arg_btn_positive"
        val ARG_BTN_NEGATIVE = "arg_btn_negative"
        val ARG_BTN_NEUTRAL = "arg_btn_natural"
    }
}
