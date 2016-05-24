package com.xianzhitech.ptt.ui.home

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.trello.rxlifecycle.FragmentEvent
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.createAvatarDrawable
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.getColorCompat
import com.xianzhitech.ptt.ext.getTintedDrawable
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.startActivityWithAnimation
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.settings.SettingsActivity
import com.xianzhitech.ptt.ui.user.EditProfileActivity

class ProfileFragment : BaseFragment(), View.OnClickListener {
    private var views : Views? = null
    private lateinit var appComponent : AppComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appComponent = context.applicationContext as AppComponent
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_profile, container, false)?.apply {
            val tintColor = context.getColorCompat(R.color.grey_700)
            findView<Button>(R.id.profile_settings).apply {
                setOnClickListener(this@ProfileFragment)
                setCompoundDrawablesWithIntrinsicBounds(
                        context.getTintedDrawable(R.drawable.ic_settings_black, tintColor),
                        null, null, null)
            }
            findView<Button>(R.id.profile_edit).apply {
                setOnClickListener(this@ProfileFragment)
                setCompoundDrawablesWithIntrinsicBounds(
                        context.getTintedDrawable(R.drawable.ic_mode_edit_black, tintColor),
                        null, null, null)
            }
            findView<Button>(R.id.profile_feedback).apply {
                setOnClickListener(this@ProfileFragment)
                setCompoundDrawablesWithIntrinsicBounds(
                        context.getTintedDrawable(R.drawable.ic_feedback_black, tintColor),
                        null, null, null)
            }
            findView<Button>(R.id.profile_about).apply {
                setOnClickListener(this@ProfileFragment)
                setCompoundDrawablesWithIntrinsicBounds(
                        context.getTintedDrawable(R.drawable.ic_info_black, tintColor),
                        null, null, null)
            }
            findView<View>(R.id.profile_logout).setOnClickListener(this@ProfileFragment)

            views = Views(this).apply {

                appComponent.signalService.loginState
                        .filter { it.currentUserID != null }
                        .switchMap { appComponent.userRepository.getUser(it.currentUserID!!).observe() }
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
                            appComponent.signalService
                                    .logout().subscribeSimple()
                            dialogInterface.dismiss()
                        })
                        .setNegativeButton(R.string.dialog_cancel, { dialogInterface, id -> dialogInterface.dismiss()})
                        .show()
            }
            R.id.profile_settings -> {
                startActivity(Intent(context, SettingsActivity::class.java))
            }
            R.id.profile_edit -> {
                activity.startActivityWithAnimation(Intent(context, EditProfileActivity::class.java),
                        R.anim.slide_in_from_right, R.anim.slide_out_to_left, R.anim.slide_in_from_left, R.anim.slide_out_to_right)
            }
            R.id.profile_feedback,
            R.id.profile_about ->
                Toast.makeText(context, "TODO", Toast.LENGTH_LONG).show()
        }
    }

    private class Views(rootView: View,
                        val iconView : ImageView = rootView.findView(R.id.profile_icon),
                        val nameView : TextView = rootView.findView(R.id.profile_name))
}
