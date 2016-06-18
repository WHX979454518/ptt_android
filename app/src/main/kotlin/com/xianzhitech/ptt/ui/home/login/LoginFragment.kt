package com.xianzhitech.ptt.ui.home.login

import android.os.Bundle
import android.support.design.widget.TextInputLayout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.LoginState
import com.xianzhitech.ptt.service.LoginStatus
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment

/**
 * 登陆界面
 * Created by fanchao on 17/12/15.
 */
class LoginFragment : BaseFragment()
        , AlertDialogFragment.OnNeutralButtonClickListener {

    private var views: Views? = null

    override fun onStart() {
        super.onStart()

        callbacks<Callbacks>()?.setTitle(R.string.login.toFormattedString(context))

        (context.applicationContext as AppComponent).signalHandler.loginState
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe { updateLoginState(it) }
    }

    private fun updateLoginState(state: LoginState) {
        views?.apply {
            val isIdle = state.status == LoginStatus.IDLE
            nameEditText.isEnabled = isIdle
            passwordEditText.isEnabled = isIdle
            loginButton.isEnabled = isIdle
        }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater!!.inflate(R.layout.fragment_login, container, false).apply {
            views = Views(this).apply {
                loginButton.setOnClickListener {
                    if (nameEditText.isEmpty()) {
                        nameEditText.error = R.string.error_input_name.toFormattedString(context)
                    } else if (passwordEditText.isEmpty()) {
                        passwordEditText.error = R.string.error_input_password.toFormattedString(context)
                    } else {
                        val signalService = (context.applicationContext as AppComponent).signalHandler
                        signalService.login(nameEditText.getString().trim(), passwordEditText.getString().trim())
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        views = null
        super.onDestroyView()
    }

    override fun onNeutralButtonClicked(fragment: AlertDialogFragment) {
        fragment.dismiss()
    }

    private class Views(
            rootView: View,
            val nameEditText: EditText = (rootView.findView<TextInputLayout>(R.id.login_nameField)).editText!!,
            val passwordEditText: EditText = (rootView.findView<TextInputLayout>(R.id.login_passwordField)).editText!!,
            val loginButton: View = rootView.findView(R.id.login_loginBtn)
    )

    interface Callbacks {
        fun setTitle(title: CharSequence)
    }
}