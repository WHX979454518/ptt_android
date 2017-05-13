package com.xianzhitech.ptt.ext

import android.app.Activity
import android.databinding.BindingAdapter
import android.databinding.InverseBindingAdapter
import android.graphics.drawable.AnimatedStateListDrawable
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.support.annotation.IdRes
import android.support.annotation.LayoutRes
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.MediaMessageBody
import com.xianzhitech.ptt.data.Message
import com.xianzhitech.ptt.data.MessageType
import com.xianzhitech.ptt.ui.util.ImageMessageThumbnailLoader

fun EditText.isEmpty() = text.isEmpty()

fun ViewGroup.inflate(@LayoutRes layout: Int, attachToRoot: Boolean = false): View =
        LayoutInflater.from(context).inflate(layout, this, attachToRoot)

inline fun <reified T : View> Activity.findView(@IdRes id: Int) = findViewById(id) as T
inline fun <reified T : View> View.findView(@IdRes id: Int) = findViewById(id) as T

fun View.setVisible(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

@set:BindingAdapter("show")
var View.show: Boolean
    set(value) = if (value) visibility = View.VISIBLE else visibility = View.GONE
    get() = visibility == View.VISIBLE

@set:BindingAdapter("string")
@get:InverseBindingAdapter(attribute = "string")
var EditText.string: String
    set(value) {
        setText(value)
    }
    get() = text.toString()


@BindingAdapter("minWidth")
fun setViewMinWidth(view: View, width: Float) {
    view.minimumWidth = width.toInt()
}

@BindingAdapter("minHeight")
fun setViewMinHeight(view: View, height: Float) {
    view.minimumHeight = height.toInt()
}

@BindingAdapter("url")
fun setImageUrl(view: ImageView, url: String) {
    Glide.with(view.context)
            .load(url)
            .into(view)
}

@BindingAdapter("animationSrc")
fun setImageAnimatedSrc(view: ImageView, drawable: Drawable) {
    view.setImageDrawable(drawable)
    if (drawable is AnimationDrawable) {
        drawable.start()
    }
}


@BindingAdapter("mediaMessage")
fun setImageMessage(view: ImageView, message: Message) {
    if ((message.type != MessageType.IMAGE && message.type != MessageType.VIDEO) || message.body !is MediaMessageBody) {
        return
    }

    val mediaBody = message.body as MediaMessageBody

    // Do we have thumbnail?
    if (mediaBody.thumbnail?.isNotBlank() ?: false) {
        Glide.with(view.context)
                .using(ImageMessageThumbnailLoader)
                .load(message)
                .into(view)
    } else if (mediaBody.url.isNotBlank()) {
        Glide.with(view.context)
                .load(mediaBody.url)
                .placeholder(R.drawable.ic_refresh_black_24dp)
                .into(view)
    } else {
        view.setImageDrawable(null)
    }
}