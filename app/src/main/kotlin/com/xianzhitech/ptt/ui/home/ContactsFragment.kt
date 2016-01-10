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
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.ContactItem
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.presenter.ContactListPresenter
import com.xianzhitech.ptt.presenter.ContactListPresenterView
import com.xianzhitech.ptt.service.provider.CreateConversationFromGroup
import com.xianzhitech.ptt.service.provider.CreateConversationFromPerson
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.room.RoomActivity
import com.xianzhitech.ptt.util.ContactComparator
import rx.Observable
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.arrayListOf
import kotlin.collections.sortWith

class ContactsFragment : BaseFragment<Void>(), ContactListPresenterView {
    private class Views(rootView: View,
                        val recyclerView: RecyclerView = rootView.findView(R.id.contacts_list),
                        val searchBox: EditText = rootView.findView(R.id.contacts_searchBox))


    private var views: Views? = null

    private lateinit var accountColors: IntArray
    private lateinit var presenter: ContactListPresenter
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        presenter = ContactListPresenter((activity.application as AppComponent).contactRepository)
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
            Observable.merge(
                    searchBox.fromTextChanged(),
                    searchBox.getString().toObservable())
                    .debounce(500, TimeUnit.MILLISECONDS)
                    .subscribe { presenter.search(it) }
        }

        presenter.attachView(this)
    }

    override fun showLoading(visible: Boolean) {
        //Do nothing
    }

    override fun showError(err: Throwable) {
        //TODO
    }

    override fun showContactList(contactList: List<ContactItem>) {
        adapter.setContactItems(contactList)
    }

    private inner class Adapter : RecyclerView.Adapter<ContactHolder>() {
        var contactItems = arrayListOf<ContactItem>()

        fun setContactItems(newItems: Collection<ContactItem>) {
            contactItems.clear()
            contactItems.addAll(newItems)
            contactItems.sortWith(ContactComparator(Locale.CHINESE))

            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactHolder {
            return ContactHolder(parent)
        }

        override fun onBindViewHolder(holder: ContactHolder, position: Int) {
            val contactItem = contactItems[position]
            holder.iconView.setImageDrawable(contactItem.getIcon(holder.iconView.context))
            holder.nameView.text = contactItem.name
            holder.itemView.setOnClickListener { v ->
                startActivity(RoomActivity.builder(context,
                        if (contactItem is Person) CreateConversationFromPerson(contactItem.id) else CreateConversationFromGroup((contactItem as Group).id)))
            }
        }

        override fun getItemCount(): Int {
            return contactItems.size
        }
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
