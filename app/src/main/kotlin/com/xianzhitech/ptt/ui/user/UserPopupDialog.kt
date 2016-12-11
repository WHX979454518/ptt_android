package com.xianzhitech.ptt.ui.user

import android.os.Bundle
import android.support.design.widget.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.CreateRoomRequest
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.widget.drawable.createDrawable
import rx.Subscription

class UserPopupDialog : BottomSheetDialogFragment() {
    private var views : Views? = null
    private var subscription : Subscription? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_user_popup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        views = Views(view).apply {
            callButton.setOnClickListener {
                (activity as? BaseActivity)?.joinRoom(CreateRoomRequest(extraMemberIds = listOf(arguments.getString(ARG_USER_ID))))
            }

            view.setOnClickListener {
                activity.startActivityWithAnimation(UserDetailsActivity.build(context, arguments.getString(ARG_USER_ID)))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        views = null
    }

    override fun onStart() {
        super.onStart()

        subscription = appComponent.userRepository
                .getUser(arguments.getString(ARG_USER_ID))
                .observe()
                .observeOnMainThread()
                .subscribeSimple { user ->
                    if (user != null) {
                        views?.let {
                            it.name.text = user.name
                            it.level.text = user.priority.toLevelString(context)
                            it.icon.setImageDrawable(user.createDrawable(context))
                        }
                    }
                }
    }

    override fun onStop() {
        super.onStop()

        subscription?.unsubscribe()
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