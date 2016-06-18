package com.xianzhitech.ptt.ui.dialog

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

    private lateinit var builder: Builder
    val attachment: Serializable?
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

    inline fun <reified T> attachmentAs() : T {
        return attachment as T
    }

    private inline fun <reified T> getParentAs(): T? {
        return parentFragment as? T ?: activity as? T
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        when (which) {
            DialogInterface.BUTTON_POSITIVE -> {
                getParentAs<OnPositiveButtonClickListener>()?.onPositiveButtonClicked(this)
            }

            DialogInterface.BUTTON_NEGATIVE -> {
                getParentAs<OnNegativeButtonClickListener>()?.onNegativeButtonClicked(this)
            }

            DialogInterface.BUTTON_NEUTRAL -> {
                getParentAs<OnNeutralButtonClickListener>()?.onNeutralButtonClicked(this)
            }
        }

        if (builder.autoDismiss) {
            dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface?) {
        super.onDismiss(dialog)
        getParentAs<OnDismissListener>()?.onDismiss(this)
    }

    override fun onCancel(dialog: DialogInterface?) {
        super.onCancel(dialog)
        getParentAs<OnCancelListener>()?.onCancelled(this)
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

    interface OnDismissListener {
        fun onDismiss(fragment: AlertDialogFragment)
    }

    interface OnCancelListener {
        fun onCancelled(fragment: AlertDialogFragment)
    }

    class Builder : Serializable {
        var title: CharSequence? = null
        var message: CharSequence? = null
        var btnPositive: CharSequence? = null
        var btnNegative: CharSequence? = null
        var btnNeutral: CharSequence? = null
        var autoDismiss: Boolean = true
        var cancellabe: Boolean = true
        var attachment: Serializable? = null

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
