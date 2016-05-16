package com.xianzhitech.ptt.ext

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.ui.widget.drawable.TextDrawable
import com.xianzhitech.ptt.ui.widget.drawable.UriDrawable

fun User.createAvatarDrawable(fragment: Fragment) = createAvatarDrawable(fragment.context, Glide.with(fragment))
fun User.createAvatarDrawable(activity: FragmentActivity) = createAvatarDrawable(activity, Glide.with(activity))
fun User.createAvatarDrawable(context: Context) = createAvatarDrawable(context, Glide.with(context))

internal fun User.createAvatarDrawable(context: Context, requestManager: RequestManager) : Drawable {
    if (avatar.isNullOrEmpty()) {
        return TextDrawable(context, name[0].toString(), context.resources.getIntArray(R.array.account_colors).let {
            it[Math.abs(name.hashCode()) % it.size]
        })
    }
    else {
        return UriDrawable(requestManager, Uri.parse(avatar))
    }
}
