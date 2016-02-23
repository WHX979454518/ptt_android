package com.xianzhitech.ptt.ui.widget

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.drawable.GlideDrawable
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.model.User
import rx.Subscription
import rx.subjects.BehaviorSubject

class UserDrawable private constructor(private val context : Any,
                                       private val userId : String,
                                       private var user : User?) : DrawableWrapper(), Target<GlideDrawable> {

    companion object {
        private val BASE_TEXT_AVATAR = Uri.parse("drawable://text/")
    }

    private val realContext : Context
    get() = if (context is Fragment) context.activity else context as Context

    private val requestManager : RequestManager
    get() = when (context) {
        is Fragment -> Glide.with(context)
        is FragmentActivity -> Glide.with(context)
        is Context -> Glide.with(context)
        else -> throw IllegalArgumentException()
    }

    // For glide target
    private var request : Request? = null
    private var sizeSubject = BehaviorSubject.create<Rect>()
    private var subscription : Subscription? = null
    private var avatar : Uri? = null
    set(value) {
        if (field != value) {
            Glide.clear(this)
            field = value
            applyAvatar()
        }
    }

    private fun applyAvatar() {
        val localAvatar = avatar
        if (localAvatar != null && localAvatar.scheme == BASE_TEXT_AVATAR.scheme && localAvatar.host == BASE_TEXT_AVATAR.host && localAvatar.pathSegments.isNotEmpty()) {
            drawable = TextDrawable(realContext, localAvatar.pathSegments[0], localAvatar.pathSegments[0].hashCode())
        } else if (localAvatar != null) {
            requestManager.load(localAvatar).crossFade().into(this)
        }
    }

    constructor(fragment : Fragment, userId : String, user : User?) : this(fragment as Any, userId, user)
    constructor(fragmentActivity: FragmentActivity, userId : String, user : User?) : this(fragmentActivity as Any, userId, user)
    constructor(context: Context, userId : String, user : User?) : this(context as Any, userId, user)

    init {
        observeUser()
    }

    private fun observeUser() {
        subscription?.apply { unsubscribe() }
        var userObservable = (realContext.applicationContext as AppComponent).userRepository.getUser(userId).observeOnMainThread()
        if (user != null) {
            userObservable = userObservable.startWith(user)
        }
        subscription = userObservable.subscribe {
            avatar = when {
                it == null -> null
                it.avatar.isNullOrBlank() -> BASE_TEXT_AVATAR.buildUpon().appendPath(it.name.first().toString()).build()
                else -> Uri.parse(it.avatar)
            }

            user = it
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        sizeSubject.onNext(bounds)
    }

    override fun onLoadStarted(placeHolder: Drawable?) {
        drawable = placeHolder
    }

    override fun getSize(callback: SizeReadyCallback) {
        sizeSubject.first { it.isEmpty.not() }
                .subscribe { callback.onSizeReady(it.width(), it.height()) }
    }

    override fun onResourceReady(drawable: GlideDrawable, animation: GlideAnimation<in GlideDrawable>?) {
        this.drawable = drawable
    }

    override fun onLoadCleared(placeHolder: Drawable?) {
        drawable = placeHolder
    }

    override fun onLoadFailed(error: Exception?, failDrawable: Drawable?) {
        drawable = failDrawable
    }

    override fun getRequest() = this.request
    override fun setRequest(newRequest: Request?) {
        this.request = newRequest
    }

    override fun onStart() {
        if (subscription == null || subscription!!.isUnsubscribed) {
            observeUser()
        }
    }

    override fun onDestroy() {
    }

    override fun onStop() {
        subscription?.unsubscribe()
        subscription = null
    }
}