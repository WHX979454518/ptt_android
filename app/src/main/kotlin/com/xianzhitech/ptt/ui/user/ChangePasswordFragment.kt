package com.xianzhitech.ptt.ui.user

import android.app.ProgressDialog
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.design.widget.TextInputLayout
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.isEmpty
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import rx.android.schedulers.AndroidSchedulers

class ChangePasswordFragment : BaseFragment(), AlertDialogFragment.OnPositiveButtonClickListener, AlertDialogFragment.OnNegativeButtonClickListener {
    private var views : Views? = null
    private val callbacks : Callbacks?
    get() = (activity as? Callbacks?)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_change_password, container, false).apply {
            views = Views(this).apply {
                oldPassword.editText?.addTextChangedListener(EditTextAutoClearErrorWatcher(oldPassword))
                newPassword.editText?.addTextChangedListener(EditTextAutoClearErrorWatcher(newPassword))
                newPasswordConfirm.editText?.addTextChangedListener(EditTextAutoClearErrorWatcher(newPasswordConfirm))
                saveButton.setOnClickListener { savePassword() }
            }
        }
    }

    override fun onDestroyView() {
        views = null
        super.onDestroyView()
    }
    
    fun requestFinish() : Boolean {
        views?.apply {
            if (oldPassword.visibility == View.VISIBLE && oldPassword.text.isNullOrEmpty().not() ||
                    (newPassword.text.isNullOrEmpty().not() || newPasswordConfirm.text.isNullOrEmpty().not())) {
                AlertDialogFragment.Builder().apply {
                    title = R.string.dialog_confirm_title.toFormattedString(context)
                    message = R.string.error_has_unsaved_change.toFormattedString(context)
                    btnPositive = R.string.quit.toFormattedString(context)
                    btnNegative = R.string.dialog_cancel.toFormattedString(context)

                }.show(childFragmentManager, TAG_CONFIRM_QUIT)

                return false
            }
        }

        return true
    }

    private fun savePassword() {
        views?.apply {
            if ((oldPassword.editText!!.isEmpty())) {
                oldPassword.error = R.string.error_input_password.toFormattedString(context)
                oldPassword.requestFocusFromTouch()
                return
            }

            if (newPassword.editText!!.isEmpty()) {
                newPassword.error = R.string.error_input_password.toFormattedString(context)
                newPassword.requestFocusFromTouch()
                return
            }

            if (newPasswordConfirm.editText!!.isEmpty()) {
                newPasswordConfirm.error = R.string.error_input_password.toFormattedString(context)
                newPasswordConfirm.requestFocusFromTouch()
                return
            }

            if (newPasswordConfirm.text != newPassword.text) {
                val error = R.string.error_password_not_same.toFormattedString(context)
                newPasswordConfirm.error = error
                newPassword.error = error
                newPassword.requestFocusFromTouch()
                return
            }

            val password = newPassword.text
            if (password == null || password.length < 6) {
                val error = R.string.error_password_not_long_enough.toFormattedString(context)
                newPasswordConfirm.error = error
                newPassword.error = error
                return
            }

            oldPassword.error = null
            newPassword.error = null
            newPasswordConfirm.error = null

            val dialog = ProgressDialog.show(context, null, R.string.saving.toFormattedString(context), true, false)
            (activity.application as AppComponent).signalService
                    .changePassword(oldPassword.text!!, password)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError {
                        if (activity != null) {
                            Toast.makeText(context, it.describeInHumanMessage(context), Snackbar.LENGTH_LONG).show()
                        }

                        dialog.dismiss()
                    }
                    .subscribeSimple {
                        dialog.dismiss()
                        if (activity != null && !activity.isFinishing) {
                            callbacks?.confirmFinishing()
                        }
                    }
        }
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_CONFIRM_QUIT -> callbacks?.confirmFinishing()
        }
    }

    override fun onNegativeButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_CONFIRM_QUIT -> fragment.dismiss()
        }
    }


    private val TextInputLayout.text : String?
        get() = editText?.text?.toString()

    private class EditTextAutoClearErrorWatcher(private val editText: TextInputLayout) : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            editText.error = null
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
    }

    private class Views(rootView : View,
                        val oldPassword : TextInputLayout = rootView.findView(R.id.changePassword_oldPassword),
                        val newPassword : TextInputLayout = rootView.findView(R.id.changePassword_newPassword),
                        val newPasswordConfirm : TextInputLayout = rootView.findView(R.id.changePassword_newPasswordConfirm),
                        val saveButton : View = rootView.findView(R.id.changePassword_save))

    interface Callbacks {
        fun confirmFinishing()
    }

    companion object {
        private const val TAG_CONFIRM_QUIT = "tag_confirm_quit"
    }
}