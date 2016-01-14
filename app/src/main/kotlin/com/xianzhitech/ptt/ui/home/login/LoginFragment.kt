package com.xianzhitech.ptt.ui.home.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.BackgroundServiceBinder
import com.xianzhitech.ptt.service.LoginState
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.home.AlertDialogFragment

/**
 * 登陆界面
 * Created by fanchao on 17/12/15.
 */
class LoginFragment : BaseFragment<LoginFragment.Callbacks>()
        , AlertDialogFragment.OnNeutralButtonClickListener {

    companion object {
        const val TAG_LOGIN_ERROR_DIALOG = "tag_login_error_dialog"
    }

    private var views: Views? = null
    private var serviceBinder: BackgroundServiceBinder? = null

    override fun onStart() {
        super.onStart()

        callbacks?.setTitle(R.string.login.toFormattedString(context))

        (context.applicationContext as AppComponent).connectToBackgroundService().flatMap { serviceBinder = it; it.loginState }
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe { updateLoginState(it) }
    }

    override fun onStop() {
        super.onStop()

        serviceBinder = null
    }

    internal fun updateLoginState(state: LoginState) {
        views?.apply {
            val isIdle = state.status == LoginState.Status.IDLE
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
                        serviceBinder?.let {
                            it.login(nameEditText.getString(), passwordEditText.getString())
                                    .observeOnMainThread()
                                    .compose(bindToLifecycle())
                                    .subscribe(GlobalSubscriber<LoginState>(context))
                        }
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
            rootView: View
            , val nameEditText: EditText = rootView.findView(R.id.login_nameField)
            , val passwordEditText: EditText = rootView.findView(R.id.login_passwordField)
            , val loginButton: View = rootView.findView(R.id.login_loginBtn)
    )

    interface Callbacks {
        fun setTitle(title: CharSequence)
    }
}