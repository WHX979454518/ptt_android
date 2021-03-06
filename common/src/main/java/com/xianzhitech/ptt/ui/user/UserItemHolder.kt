package com.xianzhitech.ptt.ui.user

import android.support.annotation.LayoutRes
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.User
import com.xianzhitech.ptt.ui.widget.drawable.createAvatarDrawable


class UserItemHolder(rootView: View,
                     val avatarView: ImageView? = rootView.findViewById(R.id.userItem_avatar) as? ImageView,
                     val nameView: TextView? = rootView.findViewById(R.id.userItem_name) as? TextView) : RecyclerView.ViewHolder(rootView) {

    constructor(parent: ViewGroup,
                @LayoutRes layout: Int) : this(LayoutInflater.from(parent.context).inflate(layout, parent, false))

    private var user: User? = null
    val userId: String?
        get() = user?.id


    fun setUser(user: User) {
        if (this.userId != user.id) {
            this.user = user
            avatarView?.setImageDrawable(user.createAvatarDrawable())
            nameView?.text = user.name
        }
    }
}