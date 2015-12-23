package com.xianzhitech.ptt.ui.home

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import butterknife.Bind
import butterknife.ButterKnife
import butterknife.OnClick
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.receiveBroadcasts
import com.xianzhitech.ptt.service.user.LoginStatus
import com.xianzhitech.ptt.service.user.UserService
import com.xianzhitech.ptt.ui.base.BaseFragment
import rx.android.schedulers.AndroidSchedulers

/**

 * 登陆界面

 * Created by fanchao on 17/12/15.
 */
class LoginFragment : BaseFragment<LoginFragment.Callbacks>() {

    @Bind(R.id.login_nameField)
    internal lateinit var nameEdit: EditText

    @Bind(R.id.login_passwordField)
    internal lateinit var passwordEdit: EditText

    @Bind(R.id.login_loginBtn)
    internal lateinit var loginBtn: View

    @Bind(R.id.login_progress)
    internal lateinit var progressBar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

        callbacks!!.setTitle(getText(R.string.login_title))

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

        context.receiveBroadcasts(UserService.ACTION_USER_LOGON_FAILED).observeOn(AndroidSchedulers.mainThread()).compose(this.bindToLifecycle<Intent>()).subscribe { intent -> AlertDialog.Builder(context).setTitle(R.string.login_failed).setMessage((intent.getSerializableExtra(UserService.EXTRA_LOGON_FAILED_REASON) as Throwable).message).create().show() }
    }

    private fun setInputEnabled(enabled: Boolean) {
        nameEdit.isEnabled = enabled
        passwordEdit.isEnabled = enabled
        loginBtn.isEnabled = enabled
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_login, container, false)
        ButterKnife.bind(this, view)
        return view
    }

    @OnClick(R.id.login_loginBtn)
    internal fun doLogin() {
        context.startService(UserService.buildLogin(context, nameEdit.text.toString(), passwordEdit.text.toString()))
        progressBar.visibility = View.VISIBLE
        setInputEnabled(false)
    }

    interface Callbacks {
        fun setTitle(title: CharSequence)
    }
}
