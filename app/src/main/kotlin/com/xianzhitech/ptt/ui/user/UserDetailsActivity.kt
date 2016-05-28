package com.xianzhitech.ptt.ui.user

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.setVisible
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.service.CreateRoomRequest
import com.xianzhitech.ptt.service.currentUserId
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity
import com.xianzhitech.ptt.ui.widget.drawable.createDrawable

class UserDetailsActivity : BaseToolbarActivity() {

    private lateinit var iconView: ImageView
    private lateinit var nameView : TextView
    private lateinit var companyNameView : TextView
    private lateinit var callButton: View

    private var user : User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_user_details)

        iconView = findView(R.id.userDetails_icon)
        nameView = findView(R.id.userDetails_name)
        companyNameView = findView(R.id.userDetails_enterpriseName)
        callButton = findView(R.id.userDetails_call)

        callButton.isEnabled = false
        callButton.setOnClickListener {
            if (user != null) {
                joinRoom(CreateRoomRequest(extraMemberIds = listOf(user!!.id)))
            }
        }
        callButton.setVisible((application as AppComponent).signalService.currentUserId != intent.getStringExtra(EXTRA_USER_ID))
    }

    override fun onStart() {
        super.onStart()

        (application as AppComponent).userRepository.getUser(intent.getStringExtra(EXTRA_USER_ID))
                .observe()
                .observeOnMainThread()
                .bindToLifecycle()
                .subscribeSimple { user ->
                    if (user == null) {
                        Toast.makeText(this@UserDetailsActivity, R.string.error_getting_user_info, Toast.LENGTH_LONG).show();
                        finish()
                    } else {
                        this@UserDetailsActivity.user = user
                        iconView.setImageDrawable(user.createDrawable(this@UserDetailsActivity))
                        nameView.text = user.name
                        companyNameView.text = user.enterpriseName
                        callButton.isEnabled = true
                    }
                }
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"

        fun build(context: Context, userId: String) : Intent {
            return Intent(context, UserDetailsActivity::class.java)
                    .putExtra(EXTRA_USER_ID, userId)
        }
    }
}