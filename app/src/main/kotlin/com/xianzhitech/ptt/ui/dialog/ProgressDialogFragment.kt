package com.xianzhitech.ptt.ui.dialog

import android.app.Dialog
import android.app.ProgressDialog
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v7.app.AppCompatDialogFragment
import java.io.Serializable


class ProgressDialogFragment : AppCompatDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = arguments.getSerializable(ARG_BUILDER) as Builder
        return ProgressDialog(context, theme).apply {
            setTitle(builder.title)
            setMessage(builder.message)
        }
    }

    class Builder : Serializable {
        var title : String? = null
        var message : String? = null

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