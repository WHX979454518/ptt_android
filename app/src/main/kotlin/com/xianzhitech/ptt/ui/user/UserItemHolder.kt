package com.xianzhitech.ptt.ui.user

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.createAvatarDrawable
import com.xianzhitech.ptt.model.User


class UserItemHolder(rootView : View,
                     val avatarView : ImageView? = rootView.findViewById(R.id.userItem_avatar) as? ImageView,
                     val nameView : TextView? = rootView.findViewById(R.id.userItem_name) as? TextView) : RecyclerView.ViewHolder(rootView) {

    private var user : User? = null
    val userId : String?
        get() = user?.id


    fun setUser(user: User) {
        if (this.userId != user.id) {
            this.user = user
            avatarView?.setImageDrawable(user.createAvatarDrawable(itemView.context))
            nameView?.text = user.name
        }
    }
}