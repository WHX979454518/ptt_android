package com.xianzhitech.ptt.ui.modellist

import android.databinding.ObservableArrayMap
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.xianzhitech.ptt.App
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.NamedModel
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel
import com.xianzhitech.ptt.ui.home.ModelProvider
import com.xianzhitech.ptt.viewmodel.ViewModel
import com.xianzhitech.ptt.util.ModelComparator
import com.xianzhitech.ptt.util.ObservableArrayList
import com.xianzhitech.ptt.util.toPinyin
import rx.Observable


open class ModelListViewModel(private val modelProvider: ModelProvider,
                              private val navigator: Navigator? = null) : LifecycleViewModel() {

    val viewModels = ObservableArrayList<ViewModel>()
    val headerViewModelPositions = ObservableField<Map<Char, Int>>(emptyMap())
    val searchTerm = ObservableField<String>()
    val loading = ObservableBoolean()
    val selectedItemIds = ObservableArrayMap<String, Boolean>()

    init {
        modelProvider.preselectedModelIds.forEach { selectedItemIds.put(it, true) }
    }

    override fun onStart() {
        super.onStart()

        Observable.combineLatest(
                modelProvider.getModels(App.instance)
                        .map { it.sortedWith(ModelComparator) }
                        .doOnLoadingState(loading::set),
                searchTerm.toRx(),
                { models, _ -> models })
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
                            ModelComparator.compare(lhs, rhs)
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

                        resultList.add(NamedModelViewModel(model, selectedItemIds, modelProvider.selectable))
                    }

                    resultList to headerPositions
                }
                .observeOnMainThread()
                .doOnLoadingState(loading::set)
                .subscribeSimple { (list, positions) ->
                    headerViewModelPositions.set(positions)
                    viewModels.replaceAll(list)
                }
                .bindToLifecycle()
    }

    open fun onClickItem(model: NamedModel) {
        if (modelProvider.selectable) {
            if (modelProvider.preselectedModelIds.contains(model.id).not() ||
                    modelProvider.preselectedUnselectable.not()) {
                if (selectedItemIds.contains(model.id)) {
                    selectedItemIds.remove(model.id)
                } else {
                    selectedItemIds.put(model.id, true)
                }
            }
            return
        } else if (model is User) {
            navigator?.navigateToUser(model)
        } else if (model is Group) {
            navigator?.navigateToGroup(model)
        }
    }

    private val NamedModel.capitalChar: Char
        get() = if (this is Group) {
            '#'
        } else {
            name.firstOrNull()?.toPinyin()?.firstOrNull() ?: '#'
        }

    interface Navigator {
        fun navigateToUser(user: User)
        fun navigateToGroup(group: Group)
    }
}
