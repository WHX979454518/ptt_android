package com.xianzhitech.ptt.ui.widget

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.xianzhitech.ptt.model.ContactItem
import rx.subjects.BehaviorSubject


class ContactDrawable private constructor(private val context : Any,
                                          private val contactItem : ContactItem): Drawable(), Target<Drawable> {

    companion object {
        fun create(fragment : Fragment, contactItem: ContactItem) = ContactDrawable(fragment, contactItem)
        fun create(activity : FragmentActivity, contactItem: ContactItem) = ContactDrawable(activity, contactItem)
        fun create(activity : Activity, contactItem: ContactItem) = ContactDrawable(activity, contactItem)
        fun create(context: Context, contactItem: ContactItem) = ContactDrawable(context, contactItem)
    }

    private var drawable : Drawable? = null
        set(value) {
            if (field != value) {
                field = value
                callback?.invalidateDrawable(this)
            }
        }

    private val boundsSubject = BehaviorSubject.create<Rect>()

    private var selfRequest: Request? = null

    private var selfColorFilter: ColorFilter? = null
    set(value) {
        if (field != value) {
            field = value
            callback?.invalidateDrawable(this)
        }
    }
    private var selfAlpha : Int = 255
    set(value) {
        if (field != value) {
            field = value
            callback?.invalidateDrawable(this)
        }
    }


    private fun createGlideRequest() : RequestManager {
        return when (context) {
            is FragmentActivity -> Glide.with(context)
            is Activity -> Glide.with(context)
            is Fragment -> Glide.with(context)
            is Context -> Glide.with(context)
            else -> throw IllegalStateException("Unknown context $context")
        }
    }

    override fun getIntrinsicHeight(): Int = drawable?.intrinsicHeight ?: -1
    override fun getIntrinsicWidth(): Int = drawable?.intrinsicWidth ?: -1

    override fun draw(canvas: Canvas?) {
        if (bounds.isEmpty) {
            return
        }

        drawable?.apply {
            val oldColorFilter = colorFilter
            val oldAlpha = alpha
            colorFilter = selfColorFilter
            alpha = selfAlpha
            bounds = this@ContactDrawable.bounds
            draw(canvas)
            colorFilter = oldColorFilter
            alpha = oldAlpha
        }
    }

    override fun setAlpha(alpha: Int) {
        selfAlpha = alpha
    }

    override fun getOpacity(): Int = drawable?.opacity ?: PixelFormat.TRANSPARENT

    override fun setColorFilter(colorFilter: ColorFilter?) {
        selfColorFilter = colorFilter
    }

    override fun onLoadCleared(placeHolder: Drawable?) {
        drawable = placeHolder
    }

    override fun getSize(sizeReadyCallback: SizeReadyCallback) {
        boundsSubject.filter { it.isEmpty.not() }
                .first()
                .subscribe { sizeReadyCallback.onSizeReady(it.width(), it.height()) }
    }

    override fun onBoundsChange(bounds: Rect?) {
        super.onBoundsChange(bounds)

        boundsSubject.onNext(bounds)
        callback?.invalidateDrawable(this)
    }

    override fun onLoadFailed(exception: Exception?, errorDrawable: Drawable?) {
        drawable = errorDrawable
    }

    override fun setRequest(request: Request?) {
        this.selfRequest = request
    }

    override fun getRequest() = selfRequest

    override fun onResourceReady(resource: Drawable?, p1: GlideAnimation<in Drawable>?) {
        drawable = resource
    }

    override fun onLoadStarted(placeHolder: Drawable?) {
        drawable = placeHolder
    }

    override fun onStart() {

    }

    override fun onDestroy() {

    }

    override fun onStop() {

    }
}