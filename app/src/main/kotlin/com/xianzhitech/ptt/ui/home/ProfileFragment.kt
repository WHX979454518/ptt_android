package com.xianzhitech.ptt.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.trello.rxlifecycle.FragmentEvent
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.getIcon
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.ui.base.BaseFragment

class ProfileFragment : BaseFragment<Unit>(), View.OnClickListener {
    private var views : Views? = null

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_profile, container, false)?.apply {
            findView<View>(R.id.profile_settings).setOnClickListener(this@ProfileFragment)
            findView<View>(R.id.profile_edit).setOnClickListener(this@ProfileFragment)
            findView<View>(R.id.profile_feedback).setOnClickListener(this@ProfileFragment)
            findView<View>(R.id.profile_about).setOnClickListener(this@ProfileFragment)
            findView<View>(R.id.profile_logout).setOnClickListener(this@ProfileFragment)

            views = Views(this).apply {
                val appComponent = context.applicationContext as AppComponent
                appComponent.connectToBackgroundService()
                        .flatMap { it.loginState }
                        .filter { it.currentUserID != null }
                        .flatMap { appComponent.userRepository.getUser(it.currentUserID!!) }
                        .compose(bindUntil(FragmentEvent.DESTROY_VIEW))
                        .observeOnMainThread()
                        .subscribe(object : GlobalSubscriber<User>(context) {
                            override fun onNext(t: User) {
                                iconView.setImageDrawable(t.getIcon(context))
                                nameView.text = t.name
                            }
                        })
            }
        }
    }

    override fun onDestroyView() {
        views = null
        super.onDestroyView()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.profile_edit,
            R.id.profile_feedback,
            R.id.profile_about,
            R.id.profile_logout,
            R.id.profile_settings ->
                Toast.makeText(context, "TODO", Toast.LENGTH_LONG).show()
        }
    }

    private class Views(rootView: View,
                        val iconView : ImageView = rootView.findView(R.id.profile_icon),
                        val nameView : TextView = rootView.findView(R.id.profile_name))
}
