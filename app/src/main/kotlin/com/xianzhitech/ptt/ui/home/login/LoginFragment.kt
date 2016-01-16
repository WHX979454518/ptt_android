package com.xianzhitech.ptt.ui.home.login

import android.app.ProgressDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.LoginState
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.home.AlertDialogFragment
import java.util.concurrent.TimeUnit

/**
 * 登陆界面
 * Created by fanchao on 17/12/15.
 */
class LoginFragment : BaseFragment<LoginFragment.Callbacks>()
        , AlertDialogFragment.OnNeutralButtonClickListener {

    private var views: Views? = null

    override fun onStart() {
        super.onStart()

        callbacks?.setTitle(R.string.login.toFormattedString(context))

        (context.applicationContext as AppComponent).connectToBackgroundService().flatMap { it.loginState }
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe { updateLoginState(it) }
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
                        val progressDialog = ProgressDialog.show(context,
                                R.string.please_wait.toFormattedString(context),
                                R.string.login_in_progress.toFormattedString(context),
                                true,
                                false)

                        context.ensureConnectivity()
                                .flatMap { (context.applicationContext as AppComponent).connectToBackgroundService() }
                                .flatMap { binder ->
                                    binder.login(nameEditText.getString(), passwordEditText.getString())
                                            .timeout(Constants.LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                            .doOnError {
                                                binder.logout().subscribe(GlobalSubscriber())
                                            }
                                }
                                .observeOnMainThread()
                                .compose(bindToLifecycle())
                                .subscribe(object : GlobalSubscriber<Unit>(context) {
                                    override fun onError(e: Throwable) {
                                        super.onError(e)
                                        progressDialog.dismiss()
                                    }

                                    override fun onNext(t: Unit) {
                                        progressDialog.dismiss()
                                    }
                                })
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