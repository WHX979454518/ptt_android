package com.xianzhitech.ptt.ui.user

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.crashlytics.android.answers.Answers
import com.crashlytics.android.answers.ContentViewEvent
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.service.CreateRoomRequest
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity
import com.xianzhitech.ptt.ui.widget.drawable.createDrawable
import com.xianzhitech.ptt.util.withUser

class UserDetailsActivity : BaseToolbarActivity() {

    private lateinit var iconView: ImageView
    private lateinit var nameView: TextView
    private lateinit var companyNameView: TextView
    private lateinit var levelView: TextView
    private lateinit var callButton: View

    private var user: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_user_details)

        iconView = findView(R.id.userDetails_icon)
        nameView = findView(R.id.userDetails_name)
        companyNameView = findView(R.id.userDetails_enterpriseName)
        levelView = findView(R.id.userDetails_priority)
        callButton = findView(R.id.userDetails_call)

        callButton.isEnabled = false
        callButton.setOnClickListener {
            if (user != null) {
                joinRoom(CreateRoomRequest(extraMemberIds = listOf(user!!.id)))
            }
        }

        findViewById(R.id.userDetails_videoChat).setOnClickListener {
            joinRoom(CreateRoomRequest(extraMemberIds = listOf(intent.getStringExtra(EXTRA_USER_ID))), true)
        }

        callButton.setVisible((application as AppComponent).signalHandler.peekCurrentUserId != intent.getStringExtra(EXTRA_USER_ID))
    }

    override fun onStart() {
        super.onStart()

        if (user != null) {
            Answers.getInstance().logContentView(ContentViewEvent().apply {
                withUser(appComponent.signalHandler.peekCurrentUserId, appComponent.signalHandler.currentUserCache.value)
                putContentType("user-details")
                putContentId(user!!.id)
            })
        }

        (application as AppComponent).userRepository.getUser(intent.getStringExtra(EXTRA_USER_ID))
                .observe()
                .observeOnMainThread()
                .subscribeSimple { user ->
                    if (user == null) {
                        Toast.makeText(this@UserDetailsActivity, R.string.error_getting_user_info, Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        this@UserDetailsActivity.user = user
                        iconView.setImageDrawable(user.createDrawable(this@UserDetailsActivity))
                        nameView.text = user.name
                        companyNameView.text = user.enterpriseName
                        levelView.text = user.priority.toLevelString(this)
                        callButton.isEnabled = true
                    }
                }
                .bindToLifecycle()
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"

        fun build(context: Context, userId: String): Intent {
            return Intent(context, UserDetailsActivity::class.java)
                    .putExtra(EXTRA_USER_ID, userId)
        }
    }
}