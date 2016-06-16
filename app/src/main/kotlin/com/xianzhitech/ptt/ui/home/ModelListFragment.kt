package com.xianzhitech.ptt.ui.home

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import com.xianzhitech.ptt.util.ModelComparator
import com.xianzhitech.ptt.util.toPinyin
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import java.util.*
import java.util.concurrent.TimeUnit

abstract class ModelListFragment : BaseFragment() {
    private class Views(rootView: View,
                        val recyclerView: RecyclerView = rootView.findView(R.id.modelListFragment_list),
                        val sideBar: SideNavigationView = rootView.findView(R.id.modelListFragment_sidebar),
                        val currCharView: TextView = rootView.findView(R.id.modelListFragment_currentChar))


    protected abstract val allModels : rx.Observable<List<Model>>

    private var views: Views? = null

    private val adapter = Adapter()
    private val searchTermSubject = BehaviorSubject.create<String>(null as String?)

    fun search(term : String) {
        searchTermSubject.onNext(term)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_model_list, container, false)?.apply {
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

    open fun onDataLoadFinished() {

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

        allModels
                .map { it.sortedWith(ModelComparator) }
                .combineWith(searchTermSubject.debounce(200, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread()).startWith(searchTermSubject.value).distinctUntilChanged())
                .map {
                    if (it.second.isNullOrBlank()) {
                        it.first
                    } else {
                        val needle = it.second.trim().toLowerCase()
                        val pinyinList = arrayListOf<String>()
                        val pinyinStringBuffer = StringBuilder()
                        val result = arrayListOf<Model>()
                        val matchPoint = hashMapOf<String, Int>()

                        it.first.forEach { model ->
                            pinyinList.clear()
                            model.name.toPinyin(pinyinList)

                            var matchedPosition : Int

                            if (pinyinList.isEmpty() || needle.containsOnlyAsciiChars().not()) {
                                // 没有拼音, 直接匹配字符串
                                matchedPosition = model.name.indexOf(needle, ignoreCase = true)
                            }
                            else {
                                // 有拼音, 先匹配首字母
                                pinyinStringBuffer.delete(0, pinyinStringBuffer.length)
                                pinyinList.forEach { it.firstOrNull()?.let { pinyinStringBuffer.append(it) } }
                                matchedPosition = pinyinStringBuffer.indexOf(needle)

                                if (matchedPosition < 0) {
                                    // 首字母匹配不到, 要匹配整个拼音字串
                                    pinyinStringBuffer.delete(0, pinyinStringBuffer.length)
                                    pinyinList.joinTo(pinyinStringBuffer, separator = "")
                                    matchedPosition = pinyinStringBuffer.indexOf(needle)
                                }
                            }

                            if (matchedPosition < 0) {
                                return@forEach
                            }

                            when (matchedPosition) {
                                0 -> {
                                    // 匹配到了首字符, 得高分
                                    matchPoint[model.id] = 10
                                    result.add(model)
                                }

                                else -> {
                                    // 匹配其它位置, 得5分
                                    matchPoint[model.id] = 5
                                    result.add(model)
                                }
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
                .subscribe(object : GlobalSubscriber<Pair<List<Model>, Map<Char, Int>>>() {
                    override fun onNext(t: Pair<List<Model>, Map<Char, Int>>) {
                        setData(t.first, t.second)
                        onDataLoadFinished()
                    }

                    override fun onError(e: Throwable) {
                        super.onError(e)
                        onDataLoadFinished()
                    }
                })
    }

    fun setData(modelList: List<Model>, headerPositions: Map<Char, Int>) {
        adapter.headerPositions.clear()
        adapter.headerPositions.putAll(headerPositions)
        adapter.data = modelList
    }

    open fun onItemClicked(viewHolder: RecyclerView.ViewHolder, model: Model) {
        when (model) {
            is Group -> activity.startActivityWithAnimation(GroupDetailsActivity.build(context, model.id))
            is User -> activity.startActivityWithAnimation(UserDetailsActivity.build(context, model.id))
        }
    }

    abstract fun onCreateModelViewHolder(container: ViewGroup): RecyclerView.ViewHolder
    abstract fun onBindModelViewHolder(viewHolder: RecyclerView.ViewHolder, model: Model)

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
                else -> onCreateModelViewHolder(parent).apply {
                    itemView.setOnClickListener {
                        onItemClicked(this, data[adapterPosition])
                    }
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = data[position]

            when (holder) {
                is SectionHeaderHolder -> {
                    holder.titleView.text = item.name
                }

                else -> onBindModelViewHolder(holder, item)
            }
        }
    }

    private class SectionHeaderHolder(container : ViewGroup) : RecyclerView.ViewHolder(LayoutInflater.from(container.context).inflate(R.layout.view_section_header, container, false)) {
        val titleView : TextView = itemView.findView(R.id.sectionHeader_title)
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