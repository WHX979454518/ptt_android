package com.xianzhitech.ptt.ui.user

import android.os.Bundle
import android.support.design.widget.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.ext.startActivityWithAnimation
import com.xianzhitech.ptt.ext.toLevelString
import com.xianzhitech.ptt.service.toast
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.widget.drawable.createAvatarDrawable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable

class UserPopupDialog : BottomSheetDialogFragment() {
    private var views : Views? = null
    private var subscription : Disposable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_user_popup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        views = Views(view).apply {
            callButton.setOnClickListener {
                (activity as? BaseActivity)?.joinRoom(userIds = listOf(arguments.getString(ARG_USER_ID)))
            }

            view.setOnClickListener {
                (activity as? BaseActivity)?.navigateToUserDetailsPage(arguments.getString(ARG_USER_ID))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        views = null
    }

    override fun onStart() {
        super.onStart()

        subscription = appComponent.storage
                .getUser(arguments.getString(ARG_USER_ID))
                .observeOn(AndroidSchedulers.mainThread())
                .logErrorAndForget(Throwable::toast)
                .subscribe { user ->
                    if (user.isPresent) {
                        views?.let {
                            it.name.text = user.get().name
                            it.level.text = user.get().priority.toLevelString()
                            it.icon.setImageDrawable(user.get().createAvatarDrawable())
                        }
                    }
                }
    }

    override fun onStop() {
        super.onStop()

        subscription?.dispose()
        subscription = null
    }

    private class Views(root : View,
                        val name : TextView = root.findView(R.id.userPopup_name),
                        val level : TextView = root.findView(R.id.userPopup_level),
                        val icon : ImageView = root.findView(R.id.userPopup_icon),
                        val callButton: View = root.findView(R.id.userPopup_call))

    companion object {
        const val ARG_USER_ID = "user_id"

        fun create(userId : String) : UserPopupDialog {
            return UserPopupDialog().apply {
                arguments = Bundle(1).apply {
                    putString(ARG_USER_ID, userId)
                }
            }
        }
    }
}