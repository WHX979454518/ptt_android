package com.xianzhitech.ptt.ui.widget

import android.content.Context
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ui.widget.drawable.createAvatarDrawable
import rx.Subscription


class UserIconView @JvmOverloads constructor(context: Context,
                                             attrs: AttributeSet? = null,
                                             defStyle: Int = 0) : AppCompatImageView(context, attrs, defStyle) {

    private var attachedToWindow: Boolean = false
    private var subscription: Subscription? = null

    var userId: String? = null
        set(value) {
            if (field != value) {
                field = value
                startLoadingUser()
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachedToWindow = true

        startLoadingUser()
    }

    override fun onDetachedFromWindow() {
        stopLoadingUser()
        attachedToWindow = false
        super.onDetachedFromWindow()
    }

    private fun startLoadingUser() {
        stopLoadingUser()

        if (attachedToWindow && userId != null) {
            setImageDrawable(null)
            subscription = context.appComponent.userRepository
                    .getUser(userId!!)
                    .observe()
                    .observeOnMainThread()
                    .subscribe {
                        setImageDrawable(it?.createAvatarDrawable())
                    }
        }
    }

    private fun stopLoadingUser() {
        subscription?.unsubscribe()
        subscription = null
    }

}