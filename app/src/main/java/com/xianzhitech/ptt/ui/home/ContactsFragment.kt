package com.xianzhitech.ptt.ui.home

import android.os.Bundle
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import butterknife.Bind
import butterknife.ButterKnife
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Broker
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.fromTextChanged
import com.xianzhitech.ptt.ext.getString
import com.xianzhitech.ptt.ext.toObservable
import com.xianzhitech.ptt.model.ContactItem
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.service.provider.CreateGroupConversationRequest
import com.xianzhitech.ptt.service.provider.CreatePersonConversationRequest
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.room.RoomActivity
import com.xianzhitech.ptt.ui.util.ResourceUtil
import com.xianzhitech.ptt.util.ContactComparator
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.*
import java.util.concurrent.TimeUnit

class ContactsFragment : BaseFragment<Void>() {

    @Bind(R.id.contacts_list)
    internal lateinit var recyclerView: RecyclerView

    @Bind(R.id.contacts_searchBox)
    internal lateinit var searchBox: EditText

    internal lateinit var accountColors: IntArray
    private val adapter = Adapter()
    private lateinit var broker: Broker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        broker = (activity.application as AppComponent).broker
        accountColors = resources.getIntArray(R.array.account_colors)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_contacts, container, false)
        ButterKnife.bind(this, view)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        val searchIcon = DrawableCompat.wrap(ResourceUtil.getDrawable(context, R.drawable.ic_search))
        DrawableCompat.setTint(searchIcon, ResourceUtil.getColor(context, R.color.secondary_text))
        searchBox.setCompoundDrawablesWithIntrinsicBounds(searchIcon, null, null, null)

        return view
    }

    override fun onStart() {
        super.onStart()

        Observable.merge(
                searchBox.fromTextChanged(),
                searchBox.getString().toObservable())
                .debounce(500, TimeUnit.MILLISECONDS)
                .flatMap { broker.getContacts(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({ adapter.setPersons(it) })
    }

    override fun onDestroyView() {
        ButterKnife.unbind(this)
        super.onDestroyView()
    }

    private inner class Adapter : RecyclerView.Adapter<ContactHolder>() {
        private val contactItems = ArrayList<ContactItem>()

        fun setPersons(newPersons: Collection<ContactItem>?) {
            this.contactItems.clear()
            if (newPersons != null) {
                this.contactItems.addAll(newPersons)
                Collections.sort(this.contactItems, ContactComparator(Locale.CHINESE))
            }

            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactHolder {
            return ContactHolder(parent)
        }

        override fun onBindViewHolder(holder: ContactHolder, position: Int) {
            val contactItem = contactItems[position]
            holder.iconView.setColorFilter(contactItem.tintColor)
            holder.nameView.text = contactItem.name
            holder.itemView.setOnClickListener { v ->
                startActivity(RoomActivity.builder(context,
                        if (contactItem is Person) CreatePersonConversationRequest(contactItem.id) else CreateGroupConversationRequest((contactItem as Group).id)))
            }
        }

        override fun getItemCount(): Int {
            return contactItems.size
        }
    }

    internal class ContactHolder(container: ViewGroup) : RecyclerView.ViewHolder(LayoutInflater.from(container.context).inflate(R.layout.view_contact_item, container, false)) {
        @Bind(R.id.contactItem_icon)
        lateinit var iconView: ImageView

        @Bind(R.id.contactItem_name)
        lateinit var nameView: TextView

        init {
            ButterKnife.bind(this, itemView)
        }
    }
}
