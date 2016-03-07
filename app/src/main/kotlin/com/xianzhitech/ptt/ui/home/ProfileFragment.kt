package com.xianzhitech.ptt.ui.home

import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AlertDialog
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
import com.xianzhitech.ptt.ext.createAvatarDrawable
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.ui.base.BaseFragment

class ProfileFragment : BaseFragment<Unit>(), View.OnClickListener {
    private var views : Views? = null
    private lateinit var appComponent : AppComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appComponent = context.applicationContext as AppComponent
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_profile, container, false)?.apply {
            findView<View>(R.id.profile_settings).setOnClickListener(this@ProfileFragment)
            findView<View>(R.id.profile_edit).setOnClickListener(this@ProfileFragment)
            findView<View>(R.id.profile_feedback).setOnClickListener(this@ProfileFragment)
            findView<View>(R.id.profile_about).setOnClickListener(this@ProfileFragment)
            findView<View>(R.id.profile_logout).setOnClickListener(this@ProfileFragment)

            views = Views(this).apply {

                appComponent.connectToBackgroundService()
                        .flatMap { it.loginState }
                        .filter { it.currentUserID != null }
                        .flatMap { appComponent.userRepository.getUser(it.currentUserID!!) }
                        .compose(bindUntil(FragmentEvent.DESTROY_VIEW))
                        .observeOnMainThread()
                        .subscribe(object : GlobalSubscriber<User?>(context) {
                            override fun onNext(t: User?) {
                                iconView.setImageDrawable(t?.createAvatarDrawable(this@ProfileFragment))
                                nameView.text = t?.name
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
            R.id.profile_logout -> {
                AlertDialog.Builder(context)
                    .setTitle(R.string.dialog_confirm_title)
                    .setMessage(R.string.log_out_confirm_message)
                    .setPositiveButton(R.string.logout, { dialogInterface: DialogInterface, i: Int ->
                        appComponent.connectToBackgroundService()
                            .flatMap { it.logout() }
                            .subscribe(GlobalSubscriber<Unit>())
                        dialogInterface.dismiss()
                    })
                    .setNegativeButton(R.string.dialog_cancel, { dialogInterface, id -> dialogInterface.dismiss()})
                    .show()
            }
            R.id.profile_edit,
            R.id.profile_feedback,
            R.id.profile_about,
            R.id.profile_settings ->
                Toast.makeText(context, "TODO", Toast.LENGTH_LONG).show()
        }
    }

    private class Views(rootView: View,
                        val iconView : ImageView = rootView.findView(R.id.profile_icon),
                        val nameView : TextView = rootView.findView(R.id.profile_name))
}
