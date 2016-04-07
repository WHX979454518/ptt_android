package com.xianzhitech.ptt.ui.home

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDialogFragment
import java.io.Serializable

/**

 * 提供一个警告对话框

 * Created by fanchao on 17/12/15.
 */
class AlertDialogFragment : AppCompatDialogFragment(), DialogInterface.OnClickListener {

    private lateinit var builder : Builder
    val attachment : Serializable?
    get() = builder.attachment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        builder = arguments.getSerializable(ARG_BUILDER) as Builder
        isCancelable = builder.cancellabe
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(context)
                .setTitle(builder.title)
                .setMessage(builder.message)
                .setPositiveButton(builder.btnPositive, this)
                .setNegativeButton(builder.btnNegative, this)
                .setNeutralButton(builder.btnNeutral, this)
                .create()
    }

    private inline fun <reified T> getParentAs(clazz: Class<T>): T? {
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

        if (builder.autoDismiss) {
            dismiss()
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

    class Builder : Serializable {
        var title: CharSequence? = null
        var message: CharSequence? = null
        var btnPositive: CharSequence? = null
        var btnNegative: CharSequence? = null
        var btnNeutral: CharSequence? = null
        var autoDismiss : Boolean = true
        var cancellabe : Boolean = true
        var attachment : Serializable? = null

        fun create(): AlertDialogFragment {
            return AlertDialogFragment().apply {
                arguments = Bundle(1).apply {
                    putSerializable(ARG_BUILDER, this@Builder)
                }
            }
        }

        fun show(fragmentManager: FragmentManager, tag: String) {
            create().show(fragmentManager, tag)
        }

    }

    companion object {
        private const val ARG_BUILDER = "arg_builder"
    }
}
