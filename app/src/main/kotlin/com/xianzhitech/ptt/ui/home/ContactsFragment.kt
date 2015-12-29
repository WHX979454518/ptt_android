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
import com.xianzhitech.ptt.Broker
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.ContactItem
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Person
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

class ContactsFragment : BaseFragment<Void>() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchBox: EditText

    private lateinit var accountColors: IntArray
    private val adapter = Adapter()
    private lateinit var broker: Broker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        broker = (activity.application as AppComponent).broker
        accountColors = resources.getIntArray(R.array.account_colors)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_contacts, container, false)?.apply {
            recyclerView = findView(R.id.contacts_list)
            searchBox = findView(R.id.contacts_searchBox)
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = adapter

            searchBox.setCompoundDrawablesWithIntrinsicBounds(
                    context.getTintedDrawable(R.drawable.ic_search, context.getColorCompat(R.color.secondary_text)), null, null, null)
        }
    }

    override fun onStart() {
        super.onStart()

        Observable.merge(
                searchBox.fromTextChanged(),
                searchBox.getString().toObservable())
                .debounce(500, TimeUnit.MILLISECONDS)
                .flatMap { broker.getContacts(it) }
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe { adapter.setPersons(it) }
    }


    private inner class Adapter : RecyclerView.Adapter<ContactHolder>() {
        private val contactItems = arrayListOf<ContactItem>()

        fun setPersons(newPersons: Collection<ContactItem>?) {
            this.contactItems.clear()
            if (newPersons != null) {
                this.contactItems.addAll(newPersons)
                this.contactItems.sortWith(ContactComparator(Locale.CHINESE))
            }

            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactHolder {
            return ContactHolder(parent)
        }

        override fun onBindViewHolder(holder: ContactHolder, position: Int) {
            val contactItem = contactItems[position]
            holder.iconView.setColorFilter(contactItem.getTintColor(holder.itemView.context))
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
