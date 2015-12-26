package com.xianzhitech.ptt.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.service.user.LoginStatus
import com.xianzhitech.ptt.service.user.UserService
import com.xianzhitech.ptt.ui.base.BaseFragment

/**

 * 登陆界面

 * Created by fanchao on 17/12/15.
 */
class LoginFragment : BaseFragment<LoginFragment.Callbacks>() {

    private lateinit var nameEdit: EditText
    private lateinit var passwordEdit: EditText
    private lateinit var loginBtn: View
    private lateinit var progressBar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

        callbacks?.setTitle(getText(R.string.login_title))

        UserService.getLoginStatus(context)
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe { status ->
                    when (status) {
                        LoginStatus.IDLE -> {
                            setInputEnabled(true)
                            progressBar.visibility = View.GONE
                        }

                        LoginStatus.LOGGED_ON -> {
                            progressBar.visibility = View.GONE
                        }

                        LoginStatus.LOGIN_IN_PROGRESS -> {
                            setInputEnabled(false)
                            progressBar.visibility = View.VISIBLE
                        }
                    }
                }

        context.receiveBroadcasts(UserService.ACTION_USER_LOGON_FAILED).observeOnMainThread().compose(this.bindToLifecycle<Intent>()).subscribe { intent -> AlertDialog.Builder(context).setTitle(R.string.login_failed).setMessage((intent.getSerializableExtra(UserService.EXTRA_LOGON_FAILED_REASON) as Throwable).message).create().show() }
    }

    private fun setInputEnabled(enabled: Boolean) {
        nameEdit.isEnabled = enabled
        passwordEdit.isEnabled = enabled
        loginBtn.isEnabled = enabled
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_login, container, false)?.apply {
            nameEdit = findView(R.id.login_nameField)
            passwordEdit = findView(R.id.login_passwordField)
            loginBtn = findView(R.id.login_loginBtn)
            progressBar = findView(R.id.login_progress)

            loginBtn.setOnClickListener { doLogin() }
        }
    }


    fun doLogin() {
        context.startService(UserService.buildLogin(context, nameEdit.text.toString(), passwordEdit.text.toString()))
        progressBar.visibility = View.VISIBLE
        setInputEnabled(false)
    }

    interface Callbacks {
        fun setTitle(title: CharSequence)
    }
}
