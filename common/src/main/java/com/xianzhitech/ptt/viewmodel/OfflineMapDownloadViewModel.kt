package com.xianzhitech.ptt.viewmodel

import android.databinding.ObservableArrayMap
import android.databinding.ObservableBoolean
import com.baidu.mapapi.map.offline.MKOLSearchRecord
import com.baidu.mapapi.map.offline.MKOfflineMap
import com.xianzhitech.ptt.ext.doOnLoading
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.ui.map.MapDownloadService
import com.xianzhitech.ptt.util.ObservableArrayList
import io.reactivex.Single


class OfflineMapDownloadViewModel : LifecycleViewModel() {
    val cityViewModels = ObservableArrayList<OfflineCityViewModel>()
    val downloadStatusMap = ObservableArrayMap<Int, Int>()
    val loading = ObservableBoolean()

    override fun onStart() {
        super.onStart()

        MapDownloadService.downloadStatusMap
                .doOnSubscribe(this::bindToLifecycle)
                .subscribe {
                    downloadStatusMap.clear()
                    downloadStatusMap.putAll(it)
                }

        Single.fromCallable {
            val map = MKOfflineMap()
            map.init { _, _ -> }
            map.offlineCityList.apply {
                map.destroy()
            }
        }
                .doOnLoading(loading::set)
                .toMaybe()
                .logErrorAndForget()
                .doOnSubscribe(this::bindToLifecycle)
                .subscribe { records ->
                    val list = arrayListOf<OfflineCityViewModel>()
                    records.forEach { it.addCityViewModels(list, 0) }
                    cityViewModels.replaceAll(list)
                }
    }

    private fun MKOLSearchRecord.addCityViewModels(out : MutableList<OfflineCityViewModel>, level: Int) {
        out.add(OfflineCityViewModel(downloadStatusMap =  downloadStatusMap, level = level, city = this))
        childCities?.forEach {
            it.addCityViewModels(out, level + 1)
        }
    }
}