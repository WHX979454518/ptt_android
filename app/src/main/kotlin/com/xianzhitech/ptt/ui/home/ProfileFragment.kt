package com.xianzhitech.ptt.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.trello.rxlifecycle.FragmentEvent
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.ui.app.AboutActivity
import com.xianzhitech.ptt.ui.app.FeedbackActivity
import com.xianzhitech.ptt.ui.app.ShareActivity
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import com.xianzhitech.ptt.ui.settings.SettingsActivity
import com.xianzhitech.ptt.ui.user.EditProfileActivity
import com.xianzhitech.ptt.ui.widget.drawable.createDrawable

class ProfileFragment : BaseFragment(), View.OnClickListener, AlertDialogFragment.OnPositiveButtonClickListener {
    private var views: Views? = null
    private lateinit var appComponent: AppComponent

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
            findView<Button>(R.id.profile_share).apply {
                setOnClickListener(this@ProfileFragment)
                setCompoundDrawablesWithIntrinsicBounds(
                        context.getTintedDrawable(R.drawable.ic_share, tintColor),
                        null, null, null)
            }
            findView<View>(R.id.profile_logout).setOnClickListener(this@ProfileFragment)

            views = Views(this).apply {

                appComponent.signalHandler.loginState
                        .filter { it.currentUserID != null }
                        .switchMap { appComponent.userRepository.getUser(it.currentUserID!!).observe() }
                        .compose(bindUntil(FragmentEvent.DESTROY_VIEW))
                        .observeOnMainThread()
                        .subscribe(object : GlobalSubscriber<User?>(context) {
                            override fun onNext(t: User?) {
                                iconView.setImageDrawable(t?.createDrawable(context))
                                nameView.text = t?.name
                            }
                        })
            }
        }
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) {
        if (fragment.tag == TAG_LOGOUT_CONFIRMATION) {
            appComponent.signalHandler.logout()
        }
    }

    override fun onDestroyView() {
        views = null
        super.onDestroyView()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.profile_logout -> {
                AlertDialogFragment.Builder().apply {
                    message = R.string.log_out_confirm_message.toFormattedString(context)
                    btnPositive = R.string.logout.toFormattedString(context)
                    btnNegative = R.string.dialog_cancel.toFormattedString(context)
                }.show(childFragmentManager, TAG_LOGOUT_CONFIRMATION)
            }

            R.id.profile_settings -> activity.startActivityWithAnimation(Intent(context, SettingsActivity::class.java))
            R.id.profile_edit -> activity.startActivityWithAnimation(Intent(context, EditProfileActivity::class.java))
            R.id.profile_feedback -> activity.startActivityWithAnimation(Intent(context, FeedbackActivity::class.java))
            R.id.profile_about -> activity.startActivityWithAnimation(Intent(context, AboutActivity::class.java))
            R.id.profile_share -> activity.startActivityWithAnimation(Intent(context, ShareActivity::class.java))
        }
    }


    private class Views(rootView: View,
                        val iconView: ImageView = rootView.findView(R.id.profile_icon),
                        val nameView: TextView = rootView.findView(R.id.profile_name))

    companion object {
        private const val TAG_LOGOUT_CONFIRMATION = "tag_logout_confirmation"
    }
}
