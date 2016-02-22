package com.xianzhitech.ptt.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.model.User

class UserDrawable private constructor(private val context : Any,
                                       private val userId : String,
                                       private var user : User?) : Drawable(), Target<Drawable> {

    private val realContext : Context
    get() = if (context is Fragment) context.activity else context as Context

    // For glide target
    private var request : Request? = null


    private var colorFilter : ColorFilter? = null

    constructor(fragment : Fragment, userId : String, user : User?) : this(fragment as Any, userId, user)
    constructor(fragmentActivity: FragmentActivity, userId : String, user : User?) : this(fragmentActivity as Any, userId, user)
    constructor(context: Context, userId : String, user : User?) : this(context as Any, userId, user)

    init {
        var userObservable = (realContext.applicationContext as AppComponent).userRepository.getUser(userId).observeOnMainThread()
        if (user != null) {
            userObservable = userObservable.startWith(user)
        }

    }

    override fun draw(canvas: Canvas) {
        throw UnsupportedOperationException()
    }

    override fun setAlpha(alpha: Int) {
        throw UnsupportedOperationException()
    }

    override fun getOpacity(): Int {
        throw UnsupportedOperationException()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        throw UnsupportedOperationException()
    }

    override fun onLoadStarted(p0: Drawable?) {
        throw UnsupportedOperationException()
    }

    override fun getSize(p0: SizeReadyCallback?) {
        throw UnsupportedOperationException()
    }

    override fun onResourceReady(p0: Drawable?, p1: GlideAnimation<in Drawable>?) {
        throw UnsupportedOperationException()
    }

    override fun onLoadCleared(p0: Drawable?) {
        throw UnsupportedOperationException()
    }

    override fun onLoadFailed(p0: Exception?, p1: Drawable?) {
        throw UnsupportedOperationException()
    }

    override fun getRequest() = this.request
    override fun setRequest(newRequest: Request?) {
        this.request = newRequest
    }

    override fun onStart() {
    }

    override fun onDestroy() {
    }

    override fun onStop() {
    }
}