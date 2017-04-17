package com.xianzhitech.ptt.ui.widget.drawable

import android.graphics.drawable.Drawable
import android.net.Uri
import com.bumptech.glide.Glide
import com.xianzhitech.ptt.App
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.User

fun User.createAvatarDrawable(): Drawable {
    val context = App.instance
    if (avatar.isNullOrEmpty()) {
        return TextDrawable(name[0].toString(), context.resources.getIntArray(R.array.account_colors).let {
            it[Math.abs(name.hashCode()) % it.size]
        })
    } else {
        return UriDrawable(Glide.with(context), Uri.parse(avatar))
    }
}
