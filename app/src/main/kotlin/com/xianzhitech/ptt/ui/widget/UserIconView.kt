package com.xianzhitech.ptt.ui.widget

import android.content.Context
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ui.widget.drawable.createAvatarDrawable
import io.reactivex.disposables.Disposable
import rx.Subscription


class UserIconView @JvmOverloads constructor(context: Context,
                                             attrs: AttributeSet? = null,
                                             defStyle: Int = 0) : ModelView(context, attrs, defStyle) {

    private var attachedToWindow: Boolean = false
    private var subscription: Disposable? = null

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
            val currentUser = context.appComponent.signalBroker.currentUser.value.orNull()
            if (currentUser?.id == userId) {
                model = currentUser
            }
            else {
                setImageDrawable(null)
                subscription = context.appComponent.storage
                        .getUsers(listOf(userId!!))
                        .firstElement()
                        .subscribe { users ->
                            model = users.firstOrNull()
                        }
            }
        }
    }

    private fun stopLoadingUser() {
        subscription?.dispose()
        subscription = null
    }

}