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
import com.xianzhitech.ptt.model.Model
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.group.GroupDetailsActivity
import com.xianzhitech.ptt.ui.user.UserDetailsActivity
import com.xianzhitech.ptt.ui.widget.drawable.createDrawable
import com.xianzhitech.ptt.util.ContactComparator
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.util.*
import java.util.concurrent.TimeUnit

class ContactsFragment : BaseFragment() {
    private class Views(rootView: View,
                        val recyclerView: RecyclerView = rootView.findView(R.id.contacts_list),
                        val searchBox: EditText = rootView.findView(R.id.contacts_searchBox))


    private var views: Views? = null

    private lateinit var accountColors: IntArray
    private val adapter = Adapter()
    private val contactItemSubject = BehaviorSubject.create<List<Model>>(emptyList())

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

        val appComponent = context.applicationContext as AppComponent
        val contactRepository = appComponent.contactRepository
        contactRepository.getContactItems()
                .observe()
                .compose(bindToLifecycle())
                .subscribeSimple {
                    contactItemSubject.onNext(it)
                }

        views?.apply {
            contactItemSubject.combineWith(searchBox.fromTextChanged().debounce(500, TimeUnit.MILLISECONDS).startWith(searchBox.getString()))
                    .observeOn(Schedulers.computation())
                    .map {
                        val needle = it.second
                        val items = if (needle.isNullOrBlank()) {
                            it.first
                        } else {
                            it.first.filter { it.name.contains(needle, ignoreCase = true) }
                        }

                        if (items is MutableList) {
                            items.apply {
                                sortWith(ContactComparator(Locale.CHINESE))
                            }
                        }
                        else {
                            items.sortedWith(ContactComparator(Locale.CHINESE))
                        }

                    }
                    .observeOnMainThread()
                    .compose(bindToLifecycle())
                    .subscribe { showContactList(it) }
        }
    }


    fun showContactList(contactList: List<Model>) {
        adapter.setContactItems(contactList)
    }

    private inner class Adapter : RecyclerView.Adapter<ContactHolder>() {
        private var contactItems = emptyList<Model>()

        fun setContactItems(newItems: List<Model>) {
            contactItems = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ContactHolder(parent)
        override fun onBindViewHolder(holder: ContactHolder, position: Int) {
            val item = contactItems[position]

            Glide.clear(holder.iconView)
            holder.nameView.text = item.name
            holder.iconView.setImageDrawable(item.createDrawable(holder.itemView.context))

            holder.itemView.setOnClickListener {
                if (item is User) {
                    activity.startActivityWithAnimation(UserDetailsActivity.build(context, item.id))
                } else if (item is Group) {
                    activity.startActivityWithAnimation(GroupDetailsActivity.build(context, item.id))
                }
            }
        }

        override fun getItemCount() = contactItems.size
    }

    private class ContactHolder(container: ViewGroup) : RecyclerView.ViewHolder(LayoutInflater.from(container.context).inflate(R.layout.view_contact_item, container, false)) {
        val iconView: ImageView
        val nameView: TextView

        init {
            iconView = itemView.findView(R.id.contactItem_icon)
            nameView = itemView.findView(R.id.contactItem_name)
        }
    }
}
