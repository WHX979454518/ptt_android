package com.xianzhitech.ptt.ui.user

import android.app.ProgressDialog
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.design.widget.TextInputLayout
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment


class ChangePasswordActivity : BaseToolbarActivity(), AlertDialogFragment.OnPositiveButtonClickListener, AlertDialogFragment.OnNegativeButtonClickListener {
    private lateinit var oldPassword : TextInputLayout
    private lateinit var newPassword : TextInputLayout
    private lateinit var newPasswordConfirmed : TextInputLayout

    private val verifyOldPassword : Boolean
        get() = intent.getBooleanExtra(EXTRA_VERIFY_OLD_PASSWORD, true)

    private class EditTextAutoClearErrorWatcher(private val editText: TextInputLayout) : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            editText.error = null
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_change_password)

        oldPassword = findView(R.id.changePassword_oldPassword)
        newPassword = findView(R.id.changePassword_newPassword)
        newPasswordConfirmed = findView(R.id.changePassword_newPasswordConfirm)

        oldPassword.editText?.addTextChangedListener(EditTextAutoClearErrorWatcher(oldPassword))
        newPassword.editText?.addTextChangedListener(EditTextAutoClearErrorWatcher(newPassword))
        newPasswordConfirmed.editText?.addTextChangedListener(EditTextAutoClearErrorWatcher(newPasswordConfirmed))

        oldPassword.setVisible(verifyOldPassword)

        toolbar.inflateMenu(R.menu.change_password)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.changePassword_save) {
                savePassword()
            }

            true
        }
    }


    override fun finish() {
        if (oldPassword.visibility == View.VISIBLE && oldPassword.text.isNullOrEmpty().not() ||
                (newPassword.text.isNullOrEmpty().not() || newPasswordConfirmed.text.isNullOrEmpty().not())) {
            AlertDialogFragment.Builder().apply {
                title = R.string.dialog_confirm_title.toFormattedString(this@ChangePasswordActivity)
                message = R.string.error_has_unsaved_change.toFormattedString(this@ChangePasswordActivity)
                btnPositive = R.string.quit.toFormattedString(this@ChangePasswordActivity)
                btnNegative = R.string.dialog_cancel.toFormattedString(this@ChangePasswordActivity)
            }.show(supportFragmentManager, TAG_CONFIRM_QUIT)

            return
        }

        super.finish()
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_CONFIRM_QUIT -> super.finish()
            else -> super.onPositiveButtonClicked(fragment)
        }
    }

    override fun onNegativeButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_CONFIRM_QUIT -> fragment.dismiss()
            else -> super.onNegativeButtonClicked(fragment)
        }
    }

    private fun savePassword() {
        if ((verifyOldPassword && oldPassword.editText!!.isEmpty())) {
            oldPassword.error = R.string.error_input_password.toFormattedString(this)
            oldPassword.requestFocusFromTouch()
            return
        }

        if (newPassword.editText!!.isEmpty()) {
            newPassword.error = R.string.error_input_password.toFormattedString(this)
            newPassword.requestFocusFromTouch()
            return
        }

        if (newPasswordConfirmed.editText!!.isEmpty()) {
            newPasswordConfirmed.error = R.string.error_input_password.toFormattedString(this)
            newPasswordConfirmed.requestFocusFromTouch()
            return
        }

        if (newPasswordConfirmed.text != newPassword.text) {
            val error = R.string.error_password_not_same.toFormattedString(this)
            newPasswordConfirmed.error = error
            newPassword.error = error
            newPassword.requestFocusFromTouch()
            return
        }

        val password = newPassword.text
        if (password == null || password.length < 6) {
            val error = R.string.error_password_not_long_enough.toFormattedString(this)
            newPasswordConfirmed.error = error
            newPassword.error = error
            return
        }

        oldPassword.error = null
        newPassword.error = null
        newPasswordConfirmed.error = null

        val dialog = ProgressDialog.show(this, null, R.string.saving.toFormattedString(this), true, false)
        (application as AppComponent).signalService
                .changePassword(verifyOldPassword, oldPassword.text, password)
                .observeOnMainThread()
                .doOnError {
                    if (!isFinishing) {
                        Snackbar.make(newPassword, it.describeInHumanMessage(this), Snackbar.LENGTH_LONG).show()
                    }

                    dialog.dismiss()
                }
                .subscribeSimple {
                    dialog.dismiss()
                    if (!isFinishing) {
                        super.finish()
                    }
                }
    }

    private val TextInputLayout.text : String?
        get() = editText?.text?.toString()

    companion object {
        const val EXTRA_VERIFY_OLD_PASSWORD = "extra_vop"

        private const val TAG_CONFIRM_QUIT = "tag_confirm_quit"
    }
}