package com.xianzhitech.ptt.ui.user

import android.app.Activity
import android.content.Intent
import android.support.annotation.LayoutRes
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.xianzhitech.ptt.ext.startActivityWithAnimation
import com.xianzhitech.ptt.model.User
import java.text.Collator
import java.util.*

open class UserListAdapter(@LayoutRes private val itemLayoutId: Int) : RecyclerView.Adapter<UserItemHolder>() {
    private val users = arrayListOf<User>()
    private val userComparator = UserComparator()
    private val clickListener = View.OnClickListener {
        onItemClicked(it.tag as UserItemHolder)
    }

    fun setUsers(newUsers : Collection<User>) {
        users.clear()
        users.addAll(newUsers)
        users.sortedWith(userComparator)
        notifyDataSetChanged()
    }

    fun getUser(position: Int) = users[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserItemHolder? {
        val item = UserItemHolder(LayoutInflater.from(parent.context).inflate(itemLayoutId, parent, false))
        item.itemView.setOnClickListener(clickListener)
        return item
    }

    override fun getItemCount(): Int {
        return users.size
    }

    override fun onBindViewHolder(holder: UserItemHolder, position: Int) {
        val user = users[position]
        holder.setUser(user)
        holder.itemView.tag = holder
    }

    open fun onItemClicked(userItemHolder: UserItemHolder) {
        val v = userItemHolder.itemView
        val userId = userItemHolder.userId!!
        (v.context as? Activity)?.startActivityWithAnimation(UserDetailsActivity.build(v.context, userId))
                ?: v.context.startActivity(UserDetailsActivity.build(v.context, userId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

}

class UserComparator : Comparator<User> {
    private val collator = Collator.getInstance(Locale.CHINESE)

    override fun compare(lhs: User, rhs: User): Int {
        if (lhs.id == rhs.id) {
            return 0
        }

        return collator.compare(lhs.name, rhs.name)
    }
}