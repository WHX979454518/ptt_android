package com.xianzhitech.ptt.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.StaticUserException
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import com.xianzhitech.ptt.ui.widget.drawable.createDrawable

class EditProfileActivity : BaseActivity(), AlertDialogFragment.OnNeutralButtonClickListener, View.OnClickListener {
    private lateinit var avatarImage: ImageView
    private lateinit var nameView: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_edit_profile)

        title = R.string.edit_profile.toFormattedString(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        avatarImage = findView(R.id.editProfile_avatarDisplay)
        nameView = findView(R.id.editProfile_nameDisplay)

        findViewById(R.id.editProfile_name)?.setOnClickListener(this)
        findViewById(R.id.editProfile_avatar)?.setOnClickListener(this)
        findViewById(R.id.editProfile_password)?.setOnClickListener(this)
    }

    override fun onStart() {
        super.onStart()

        val comp = (application as AppComponent)
        val currUserId = comp.signalHandler.peekCurrentUserId
        if (currUserId == null) {
            AlertDialogFragment.Builder().apply {
                title = R.string.error_title.toFormattedString(this@EditProfileActivity)
                message = R.string.error_user_not_logon.toFormattedString(this@EditProfileActivity)
                btnNeutral = R.string.dialog_ok.toFormattedString(this@EditProfileActivity)
            }.show(supportFragmentManager, TAG_ERROR_USER_NOT_LOGON)

            return
        }

        comp.userRepository.getUser(currUserId).observe()
                .first()
                .map { it ?: throw StaticUserException(R.string.error_no_such_user) }
                .observeOnMainThread()
                .doOnError {
                    Toast.makeText(this, it.describeInHumanMessage(this), Toast.LENGTH_LONG).show()
                    finish()
                }
                .subscribeSimple { user ->
                    nameView.text = user.name
                    avatarImage.setImageDrawable(user.createDrawable())
                }
                .bindToLifecycle()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.editProfile_name, R.id.editProfile_avatar -> Toast.makeText(this, "TODO", Toast.LENGTH_LONG).show()
            R.id.editProfile_password -> startActivityWithAnimation(Intent(this, ChangePasswordActivity::class.java),
                    R.anim.slide_in_from_right, R.anim.slide_out_to_left, R.anim.slide_in_from_left, R.anim.slide_out_to_right)
        }
    }

    override fun onNeutralButtonClicked(fragment: AlertDialogFragment) {
        if (fragment.tag == TAG_ERROR_USER_NOT_LOGON) {
            fragment.dismissImmediately()
            finish()
        } else {
            super.onNeutralButtonClicked(fragment)
        }
    }

    companion object {
        const val TAG_ERROR_USER_NOT_LOGON = "tag_error_user"
    }
}