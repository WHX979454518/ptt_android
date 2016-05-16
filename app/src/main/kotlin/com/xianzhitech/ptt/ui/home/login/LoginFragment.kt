package com.xianzhitech.ptt.ui.home.login

import android.app.ProgressDialog
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.design.widget.TextInputLayout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.ensureConnectivity
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.getString
import com.xianzhitech.ptt.ext.isEmpty
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.service.LoginState
import com.xianzhitech.ptt.service.LoginStatus
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import java.util.concurrent.TimeUnit

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

        (context.applicationContext as AppComponent).signalService.loginState
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
                        val progressDialog = ProgressDialog.show(context,
                                R.string.please_wait.toFormattedString(context),
                                R.string.login_in_progress.toFormattedString(context),
                                true, false)

                        val signalService = (context.applicationContext as AppComponent).signalService
                        context.ensureConnectivity()
                                .flatMap { signalService.login(nameEditText.getString(), passwordEditText.getString()) }
                                .timeout(Constants.LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                .observeOnMainThread()
                                .compose(bindToLifecycle())
                                .subscribe(object : GlobalSubscriber<Unit>() {
                                    override fun onError(e: Throwable) {
                                        super.onError(e)
                                        progressDialog.dismiss()
                                        signalService.logout().subscribeSimple(context)
                                        Snackbar.make(rootView, e.describeInHumanMessage(context), Snackbar.LENGTH_LONG).show()
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
            rootView: View,
            val nameEditText: EditText = (rootView.findView<TextInputLayout>(R.id.login_nameField)).editText!!,
            val passwordEditText: EditText = (rootView.findView<TextInputLayout>(R.id.login_passwordField)).editText!!,
            val loginButton: View = rootView.findView(R.id.login_loginBtn)
    )

    interface Callbacks {
        fun setTitle(title: CharSequence)
    }
}