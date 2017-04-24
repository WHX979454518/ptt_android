package com.xianzhitech.ptt.ui.widget

import android.content.Context
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import com.xianzhitech.ptt.data.User
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ui.widget.drawable.createAvatarDrawable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable


class UserIconView @JvmOverloads constructor(context: Context,
                                             attrs: AttributeSet? = null,
                                             defStyle: Int = 0) : AppCompatImageView(context, attrs, defStyle) {

    private var attachedToWindow: Boolean = false
    private var subscription: Disposable? = null

    var onUserLoadedListener: OnUserLoadedListener? = null

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
                setImageDrawable(currentUser!!.createAvatarDrawable())
                onUserLoadedListener?.onUserLoaded(currentUser)
            } else {
                setImageDrawable(null)
                subscription = context.appComponent.storage
                        .getUser(userId!!)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            setImageDrawable(it.transform { it!!.createAvatarDrawable() }.orNull())
                            if (it.isPresent) {
                                onUserLoadedListener?.onUserLoaded(it.get())
                            }
                        }
            }
        }
    }

    private fun stopLoadingUser() {
        subscription?.dispose()
        subscription = null
    }

    interface OnUserLoadedListener {
        fun onUserLoaded(user: User)
    }
}