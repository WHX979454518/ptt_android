package com.xianzhitech.ptt.ui.home

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Model
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.group.GroupDetailsActivity
import com.xianzhitech.ptt.ui.user.UserDetailsActivity
import com.xianzhitech.ptt.ui.widget.RecyclerAdapter
import com.xianzhitech.ptt.ui.widget.SideNavigationView
import com.xianzhitech.ptt.ui.widget.drawable.createDrawable
import com.xianzhitech.ptt.util.ModelComparator
import com.xianzhitech.ptt.util.toPinyin
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import java.util.*
import java.util.concurrent.TimeUnit

class ContactsFragment : BaseFragment() {
    private class Views(rootView: View,
                        val recyclerView: RecyclerView = rootView.findView(R.id.contacts_list),
                        val sideBar: SideNavigationView = rootView.findView(R.id.contacts_sidebar),
                        val currCharView: TextView = rootView.findView(R.id.contacts_currentChar))


    private var views: Views? = null

    private lateinit var accountColors: IntArray
    private val adapter = Adapter()
    private val searchTermSubject = BehaviorSubject.create<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accountColors = resources.getIntArray(R.array.account_colors)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.contacts, menu)
        val searchView = menu.findItem(R.id.contacts_search).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                //TODO:
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                searchTermSubject.onNext(newText)
                return true
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_contacts, container, false)?.apply {
            views = Views(this).apply {
                recyclerView.layoutManager = LinearLayoutManager(context)
                recyclerView.adapter = adapter

                val dismiss = Runnable { currCharView.visibility = View.GONE }
                sideBar.onNavigateListener = object : SideNavigationView.OnNavigationListener {
                    var lastNavigateString : String? = null

                    override fun onNavigateTo(c: String) {
                        currCharView.text = c
                        currCharView.visibility = View.VISIBLE
                        currCharView.removeCallbacks(dismiss)

                        if (lastNavigateString != c) {
                            lastNavigateString = c
                            navigateTo(c)
                        }
                    }

                    override fun onNavigateCancel() {
                        currCharView.removeCallbacks(dismiss)
                        currCharView.postDelayed(dismiss, 1000)
                    }

                }
            }
        }
    }

    private fun navigateTo(c: String) {
        views?.apply {
            if (c.isNullOrBlank()) {
                return
            }

            adapter.headerPositions.floorEntry(c.first().toLowerCase())?.let {
                logtagd("ContactsFragment", "scroll to position ${it.value}")
                (recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(it.value, 0)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        appComponent.contactRepository.getContactItems().observe()
                .map { it.sortedWith(ModelComparator) }
                .combineWith(
                        searchTermSubject.debounce(500, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                                .startWith(null as String?)
                )
                .map {
                    if (it.second.isNullOrBlank()) {
                        it.first
                    } else {
                        val needle = it.second.trim().toLowerCase()
                        val pinyinList = arrayListOf<String>()
                        val capitals = StringBuilder()
                        val result = arrayListOf<Model>()
                        val matchPoint = hashMapOf<String, Int>()

                        it.first.forEach { model ->
                            pinyinList.clear()
                            model.name.toPinyin(pinyinList)

                            if (pinyinList.isEmpty()) {
                                // 没有拼音, 直接匹配字符串
                                when (model.name.indexOf(needle, ignoreCase = true)) {
                                    0 -> {
                                        matchPoint[model.id] = 10
                                        result.add(model)
                                    }

                                    -1 -> {}
                                    else -> {
                                        matchPoint[model.id] = 5
                                        result.add(model)
                                    }
                                }

                                return@forEach
                            }

                            capitals.delete(0, capitals.length)
                            pinyinList.forEach { it.firstOrNull()?.let { capitals.append(it) } }

                            when (capitals.indexOf(needle)) {
                                0 -> {
                                    // 如果首字母匹配, 得10分
                                    matchPoint[model.id] = 10
                                    result.add(model)
                                    return@forEach
                                }

                                -1 -> {
                                    // 没有字母匹配
                                }

                                else -> {
                                    // 匹配其它位置, 得5分
                                    matchPoint[model.id] = 5
                                    result.add(model)
                                    return@forEach
                                }
                            }

                            // 从拼音全文中查找, 这个得0分
                            if (pinyinList.find { it.contains(needle) } != null) {
                                matchPoint[model.id] = 0
                                result.add(model)
                            }
                        }

                        // 按查找分数对结果排序
                        result.sortWith(Comparator<Model> { lhs, rhs ->
                            val rc = matchPoint[rhs.id]!!.compareTo(matchPoint[lhs.id]!!)
                            if (rc != 0) {
                                rc
                            } else {
                                ModelComparator.compare(lhs, rhs)
                            }
                        })

                        result
                    }
                }
                .map {
                    val resultList = ArrayList<Model>(it.size + Math.min(it.size, 27))
                    val headerPositions = hashMapOf<Char, Int>()
                    var lastChar : Char? = null

                    it.forEachIndexed { i, model ->
                        val currChar = model.capitalChar
                        if (lastChar == null || currChar != lastChar) {
                            lastChar = currChar
                            headerPositions[currChar] = resultList.size
                            resultList.add(HeaderModel(currChar.toString()))
                        }

                        resultList.add(model)
                    }

                    resultList to headerPositions
                }
                .observeOnMainThread()
                .subscribe { showContactList(it.first, it.second) }
    }

    fun showContactList(contactList: List<Model>, headerPositions: Map<Char, Int>) {
        adapter.headerPositions.clear()
        adapter.headerPositions.putAll(headerPositions)
        adapter.data = contactList
    }

    private inner class Adapter : RecyclerAdapter<Model, RecyclerView.ViewHolder>() {
        val headerPositions = TreeMap<Char, Int>()

        override fun getItemViewType(position: Int): Int {
            return when (data[position]) {
                is HeaderModel -> VIEW_TYPE_HEADER
                else -> VIEW_TYPE_ITEM
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_HEADER -> SectionHeaderHolder(parent)
                else -> ContactHolder(parent).apply {
                    itemView.setOnClickListener {
                        val item = data.getOrNull(adapterPosition)
                        if (item is User) {
                            activity.startActivityWithAnimation(UserDetailsActivity.build(context, item.id))
                        } else if (item is Group) {
                            activity.startActivityWithAnimation(GroupDetailsActivity.build(context, item.id))
                        }
                    }
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = data[position]

            when (holder) {
                is ContactHolder -> {
                    Glide.clear(holder.iconView)
                    holder.nameView.text = item.name
                    holder.iconView.setImageDrawable(item.createDrawable(holder.itemView.context))
                }

                is SectionHeaderHolder -> {
                    holder.titleView.text = item.name
                }
            }
        }
    }

    private class SectionHeaderHolder(container : ViewGroup) : RecyclerView.ViewHolder(LayoutInflater.from(container.context).inflate(R.layout.view_contact_header, container, false)) {
        val titleView : TextView = itemView.findView(R.id.contactHeader_title)
    }

    private class ContactHolder(container: ViewGroup) : RecyclerView.ViewHolder(LayoutInflater.from(container.context).inflate(R.layout.view_contact_item, container, false)) {
        val iconView: ImageView
        val nameView: TextView

        init {
            iconView = itemView.findView(R.id.contactItem_icon)
            nameView = itemView.findView(R.id.contactItem_name)
        }
    }

    private class HeaderModel(override val name: String) : Model {
        override val id: String
            get() = throw UnsupportedOperationException()
    }

    private val Model.capitalChar : Char
        get() = if (this is Group) {
            '#'
        } else {
            name.firstOrNull()?.toPinyin()?.firstOrNull() ?: '#'
        }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }
}
