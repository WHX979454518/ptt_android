package com.xianzhitech.ptt.ui.home.login

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.view.LayoutInflater
import android.view.ViewGroup
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.databinding.ConsoleLoginFragmentBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.startActivityWithAnimation
import com.xianzhitech.ptt.ui.app.AboutActivity
import com.xianzhitech.ptt.ui.app.ShareActivity
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment
import com.xianzhitech.ptt.viewmodel.LoginViewModel

/**
 * 登陆界面
 *
 * Created by fanchao on 17/12/15.
 */
class ConsoleLoginFragment : BaseViewModelFragment<LoginViewModel, ConsoleLoginFragmentBinding>(), LoginViewModel.Navigator {
    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): ConsoleLoginFragmentBinding {
        return ConsoleLoginFragmentBinding.inflate(inflater, container, false)
    }

    override fun onCreateViewModel(): LoginViewModel {
        return LoginViewModel(appComponent, this, arguments?.getBoolean(ARG_KICKED_OUT) ?: false, arguments?.getString(ARG_KICKED_OUT_REASON))
    }

    override fun navigateToHome() {
        callbacks<Callbacks>()!!.navigateToHome()
    }

    override fun navigateToShare() {
        activity.startActivityWithAnimation(Intent(context, ShareActivity::class.java))
    }

    override fun navigateToAbout() {
        activity.startActivityWithAnimation(Intent(context, AboutActivity::class.java))
    }

    override fun displayTimeoutError() {
        Snackbar.make(view!!, R.string.error_login_timeout, Snackbar.LENGTH_LONG).show()
    }

    interface Callbacks {
        fun navigateToHome()
    }

    companion object {
        const val ARG_KICKED_OUT = "kicked_out"
        const val ARG_KICKED_OUT_REASON = "kicked_out_reason"

        fun create(kickedOut : Boolean, reason : String? = null) : ConsoleLoginFragment {
            return ConsoleLoginFragment().apply {
                arguments = Bundle(2).apply {
                    putBoolean(ARG_KICKED_OUT, kickedOut)
                    putString(ARG_KICKED_OUT_REASON, reason)
                }
            }
        }
    }
}