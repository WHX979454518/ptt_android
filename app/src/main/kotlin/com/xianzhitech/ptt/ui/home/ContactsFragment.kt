package com.xianzhitech.ptt.ui.home

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.GroupContactItem
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.model.UserContactItem
import com.xianzhitech.ptt.repo.GroupWithMembers
import com.xianzhitech.ptt.service.provider.CreateRoomFromGroup
import com.xianzhitech.ptt.service.provider.CreateRoomFromUser
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.room.joinRoom
import com.xianzhitech.ptt.ui.widget.MultiDrawable
import com.xianzhitech.ptt.util.ContactComparator
import rx.Observable
import java.util.*
import java.util.concurrent.TimeUnit

class ContactsFragment : BaseFragment<Void>() {
    private class Views(rootView: View,
                        val recyclerView: RecyclerView = rootView.findView(R.id.contacts_list),
                        val searchBox: EditText = rootView.findView(R.id.contacts_searchBox))


    private var views: Views? = null

    private lateinit var accountColors: IntArray
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accountColors = resources.getIntArray(R.array.account_colors)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_contacts, container, false)?.apply {
            views = Views(this).apply {
                recyclerView.layoutManager = LinearLayoutManager(context)
                recyclerView.adapter = adapter

                searchBox.setCompoundDrawablesWithIntrinsicBounds(
                        context.getTintedDrawable(R.drawable.ic_search, context.getColorCompat(R.color.secondary_text)), null, null, null)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        views?.apply {
            val appComponent = context.applicationContext as AppComponent
            val contactRepository = appComponent.contactRepository
            val groupRepo = appComponent.groupRepository
            val userRepo = appComponent.userRepository
            searchBox.fromTextChanged().debounce(500, TimeUnit.MILLISECONDS).startWith(searchBox.getString())
                    .flatMap {
                        if (it.isNullOrBlank()) {
                            contactRepository.getContactItems()
                        }
                        else {
                            contactRepository.searchContactItems(it)
                        }
                    }
                    .flatMap {
                        val (groupIds, userIds) = it.partition { it is GroupContactItem }
                        Observable.combineLatest(
                                groupRepo.getGroupsWithMembers(groupIds.map { (it as GroupContactItem).groupId }, 9).first(),
                                userRepo.getUsers(userIds.map { (it as UserContactItem).userId }).first(),
                                { first, second -> first + second }
                        )
                    }
                    .observeOnMainThread()
                    .compose(bindToLifecycle())
                    .subscribe { showContactList(it) }
        }
    }


    fun showContactList(contactList: List<Any>) {
        adapter.setContactItems(contactList)
    }

    private inner class Adapter : RecyclerView.Adapter<ContactHolder>() {
        var contactItems = arrayListOf<Any>()

        fun setContactItems(newItems: Collection<Any>) {
            contactItems.clear()
            contactItems.addAll(newItems)
            contactItems.sortWith(ContactComparator(Locale.CHINESE))

            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ContactHolder(parent)
        override fun onBindViewHolder(holder: ContactHolder, position: Int) {
            val contactItem = contactItems[position]

            Glide.clear(holder.iconView)
            if (contactItem is GroupWithMembers) {
                if (contactItem.group.avatar.isNullOrBlank()) {
                    val groupDrawable : MultiDrawable = if (holder.iconView.drawable is MultiDrawable) {
                        holder.iconView.drawable as MultiDrawable
                    } else {
                        MultiDrawable(holder.iconView.context).apply { holder.iconView.setImageDrawable(this) }
                    }

                    groupDrawable.children = emptyList()
                }
                else {
                    Glide.with(this@ContactsFragment)
                            .load(contactItem.group.avatar)
                            .crossFade()
                            .into(holder.iconView)

                    if (contactItem !is Group) {

                    }
                }
            }
            else if (contactItem is User) {

            }

            holder.iconView.setImageDrawable(contactItem.getIcon(holder.iconView.context))
            holder.nameView.text = contactItem.name
            holder.itemView.setOnClickListener { v ->
                context.joinRoom(null,
                        if (contactItem is User) CreateRoomFromUser(contactItem.id) else CreateRoomFromGroup((contactItem as Group).id),
                        false,
                        bindToLifecycle())
            }
        }

        override fun getItemCount() = contactItems.size
    }

    internal class ContactHolder(container: ViewGroup) : RecyclerView.ViewHolder(LayoutInflater.from(container.context).inflate(R.layout.view_contact_item, container, false)) {
        lateinit var iconView: ImageView
        lateinit var nameView: TextView

        init {
            iconView = itemView.findView(R.id.contactItem_icon)
            nameView = itemView.findView(R.id.contactItem_name)
        }
    }
}
