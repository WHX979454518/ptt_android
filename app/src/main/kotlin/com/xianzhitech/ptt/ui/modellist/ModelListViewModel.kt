package com.xianzhitech.ptt.ui.modellist

import android.databinding.ObservableArrayMap
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.xianzhitech.ptt.data.ContactGroup
import com.xianzhitech.ptt.data.NamedModel
import com.xianzhitech.ptt.data.ContactUser
import com.xianzhitech.ptt.ext.containsOnlyAsciiChars
import com.xianzhitech.ptt.ext.doOnLoading
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.ext.toRxObservable
import com.xianzhitech.ptt.util.ObservableArrayList
import com.xianzhitech.ptt.util.PinyinComparator
import com.xianzhitech.ptt.util.toPinyin
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel
import com.xianzhitech.ptt.viewmodel.ViewModel
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import java.util.*


abstract class ModelListViewModel(val selectable: Boolean = false,
                                  private val preselectedIds: Collection<String> = emptyList(),
                                  private val preselectedUnselectable: Boolean = false) : LifecycleViewModel() {

    val viewModels = ObservableArrayList<ViewModel>()
    val headerViewModelPositions = ObservableField<Map<Char, Int>>(emptyMap())
    val searchTerm = ObservableField<String>()
    val loading = ObservableBoolean()
    val selectedItemIds = ObservableArrayMap<String, Boolean>().apply {
        preselectedIds.forEach { put(it, true) }
    }

    abstract val contactModels: Observable<List<NamedModel>>

    override fun onStart() {
        super.onStart()


        Observable.combineLatest(
                contactModels.map { it.sortedWith(ContactModelComparator) }.doOnLoading(loading::set),
                searchTerm.toRxObservable(),
                BiFunction<List<NamedModel>, String, List<NamedModel>> { models, _ -> models })
                .map { models ->
                    if (searchTerm.get() == null || searchTerm.get().trim().isNullOrBlank()) {
                        return@map models
                    }

                    val needle = searchTerm.get().trim().toLowerCase()
                    val pinyinList = arrayListOf<String>()
                    val pinyinStringBuffer = StringBuilder()
                    val result = arrayListOf<NamedModel>()
                    val matchPoint = hashMapOf<String, Int>()

                    models.forEach { model ->
                        pinyinList.clear()
                        model.name.toPinyin(pinyinList)

                        var matchedPosition: Int

                        if (pinyinList.isEmpty() || needle.containsOnlyAsciiChars().not()) {
                            // 没有拼音, 直接匹配字符串
                            matchedPosition = model.name.indexOf(needle, ignoreCase = true)
                        } else {
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
                    result.sortWith(Comparator<NamedModel> { lhs, rhs ->
                        val rc = matchPoint[rhs.id]!!.compareTo(matchPoint[lhs.id]!!)
                        if (rc != 0) {
                            rc
                        } else {
                            ContactModelComparator.compare(lhs, rhs)
                        }
                    })

                    result
                }
                .map {
                    val resultList = ArrayList<ViewModel>(it.size + Math.min(it.size, 27))
                    val headerPositions = hashMapOf<Char, Int>()
                    var lastChar: Char? = null

                    it.forEach { model ->
                        val currChar = model.capitalChar
                        if (lastChar == null || currChar != lastChar) {
                            lastChar = currChar
                            headerPositions[currChar] = resultList.size
                            resultList.add(HeaderViewModel(currChar.toString()))
                        }

                        resultList.add(ContactModelViewModel(model, selectedItemIds, selectable))
                    }

                    resultList to headerPositions
                }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnLoading(loading::set)
                .logErrorAndForget()
                .subscribe { (list, positions) ->
                    headerViewModelPositions.set(positions)
                    viewModels.replaceAll(list)
                }
                .bindToLifecycle()
    }

    fun onClickItem(model: NamedModel) {
        if (selectable) {
            if (preselectedIds.contains(model.id).not() ||
                    preselectedUnselectable.not()) {
                if (selectedItemIds.contains(model.id)) {
                    selectedItemIds.remove(model.id)
                } else {
                    selectedItemIds.put(model.id, true)
                }
            }
            return
        } else if (model is ContactUser) {
            onClickUser(model)
        } else if (model is ContactGroup) {
            onClickGroup(model)
        }
    }

    open fun onClickUser(user: ContactUser) {

    }

    open fun onClickGroup(group: ContactGroup) {

    }

    private val NamedModel.capitalChar: Char
        get() = if (this is ContactGroup) {
            '#'
        } else {
            name.firstOrNull()?.toPinyin()?.firstOrNull() ?: '#'
        }

    private object ContactModelComparator : Comparator<NamedModel> {
        override fun compare(lhs: NamedModel, rhs: NamedModel): Int {
            val lhsIsGroup = lhs is ContactGroup
            val rhsIsGroup = rhs is ContactGroup

            if (lhsIsGroup && rhsIsGroup || (lhsIsGroup.not() && rhsIsGroup.not())) {
                return PinyinComparator.compare(lhs.name, rhs.name)
            } else if (lhsIsGroup) {
                return -1
            } else {
                return 1
            }
        }
    }
}
