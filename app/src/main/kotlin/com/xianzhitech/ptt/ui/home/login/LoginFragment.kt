package com.xianzhitech.ptt.ui.home.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.getString
import com.xianzhitech.ptt.ext.isEmpty
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.presenter.LoginPresenter
import com.xianzhitech.ptt.presenter.LoginPresenterView
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.home.AlertDialogFragment

/**
 * 登陆界面
 * Created by fanchao on 17/12/15.
 */
class LoginFragment : BaseFragment<LoginFragment.Callbacks>()
        , LoginPresenterView
        , BackPressable
        , AlertDialogFragment.OnNeutralButtonClickListener {

    companion object {
        const val TAG_LOGIN_ERROR_DIALOG = "tag_login_error_dialog"
    }

    private var views: Views? = null
    private lateinit var presenter: LoginPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        presenter = (context.applicationContext as AppComponent).loginPresenter
    }

    override fun onStart() {
        super.onStart()

        callbacks?.setTitle(R.string.login.toFormattedString(context))
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
                        presenter.requestLogin(nameEditText.getString(), passwordEditText.getString())
                    }
                }
            }
        }
    }

    override fun onBackPressed() = presenter.cancelLogin()

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.attachView(this)
    }

    override fun onDestroyView() {
        presenter.detachView(this)
        views = null
        super.onDestroyView()
    }

    override fun showLoading(visible: Boolean) {
        views?.apply {
            loginButton.isEnabled = visible.not()
            nameEditText.isEnabled = visible.not()
            passwordEditText.isEnabled = visible.not()
        }
    }

    override fun onNeutralButtonClicked(fragment: AlertDialogFragment) {
        fragment.dismiss()
    }

    override fun showLogin() {
        // Do nothing
    }

    override fun showLoginSuccess() {
        // Do nothing
    }

    override fun showError(err: Throwable) {
        AlertDialogFragment.Builder()
                .setTitle(R.string.error_title.toFormattedString(context))
                .setMessage(R.string.error_content.toFormattedString(context, err.message ?: R.string.error_unknown.toFormattedString(context)))
                .setBtnNeutral(R.string.dialog_ok.toFormattedString(context))
                .show(childFragmentManager, TAG_LOGIN_ERROR_DIALOG)
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