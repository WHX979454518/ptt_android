package com.xianzhitech.ptt.ui.user

import android.support.annotation.LayoutRes
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.xianzhitech.ptt.data.User
import com.xianzhitech.ptt.ui.base.BaseActivity
import java.text.Collator
import java.util.*

open class UserListAdapter(@LayoutRes private val itemLayoutId: Int) : RecyclerView.Adapter<UserItemHolder>() {
    private val users = arrayListOf<User>()
    private val userComparator = UserComparator()
    private val clickListener = View.OnClickListener {
        onItemClicked(it.tag as UserItemHolder)
    }

    fun setUsers(newUsers: Collection<User>) {
        users.clear()
        users.addAll(newUsers)
        users.sortedWith(userComparator)
        notifyDataSetChanged()
    }

    fun setUserToPosition(userId : String, position : Int) {
        val oldPosition = users.indexOfFirst { it.id == userId }
        if (oldPosition >= 0 && oldPosition != position && position >= 0 && position < users.size) {
            val tmpUser = users[position]
            users[position] = users[oldPosition]
            users[oldPosition] = tmpUser
            notifyDataSetChanged()
        }
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
        (v.context as? BaseActivity)?.navigateToUserDetailsPage(userId)
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