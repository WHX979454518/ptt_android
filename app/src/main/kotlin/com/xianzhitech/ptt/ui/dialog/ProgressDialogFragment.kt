package com.xianzhitech.ptt.ui.dialog

import android.app.Dialog
import android.app.ProgressDialog
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v7.app.AppCompatDialogFragment
import java.io.Serializable


class ProgressDialogFragment : AppCompatDialogFragment() {

    val builder : Builder by lazy { arguments.getSerializable(ARG_BUILDER) as Builder }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isCancelable = builder.cancelable
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return ProgressDialog(context, theme).apply {
            setTitle(builder.title)
            setMessage(builder.message)
        }
    }

    class Builder : Serializable {
        var title : String? = null
        var message : String? = null
        var cancelable: Boolean = false

        fun showImmediately(fragmentManager: FragmentManager, tag: String) : ProgressDialogFragment {
            return ProgressDialogFragment().apply {
                this.arguments = Bundle(1).apply { putSerializable(ARG_BUILDER, this@Builder) }
                this.show(fragmentManager, tag)
                fragmentManager.executePendingTransactions()
            }
        }
    }

    companion object {
        val ARG_BUILDER = "arg_builder"
    }
}