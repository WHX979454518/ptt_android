package com.xianzhitech.ptt.ui.widget.drawable

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.drawable.GlideDrawable
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.xianzhitech.ptt.ui.widget.DrawableWrapper
import rx.subjects.BehaviorSubject


class UriDrawable constructor(requestManager: RequestManager,
                              private val uri : Uri,
                              placeholder : Drawable? = null,
                              errorDrawable: Drawable? = null) : DrawableWrapper(placeholder), Target<GlideDrawable> {

    // For glide target
    private var request : Request? = null
    private var sizeSubject = BehaviorSubject.create<Rect>()


    init {
        requestManager.load(uri)
                .placeholder(placeholder)
                .error(errorDrawable)
                .crossFade()
                .into(this)
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

    override fun toString() = "UriDrawable(uri=$uri)"
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