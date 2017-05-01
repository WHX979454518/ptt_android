package com.xianzhitech.ptt.viewmodel

import android.databinding.ObservableField
import android.databinding.ObservableMap
import com.baidu.mapapi.map.offline.MKOLSearchRecord
import com.xianzhitech.ptt.ext.createCompositeObservable
import com.xianzhitech.ptt.ui.map.MapDownloadService


data class OfflineCityViewModel(val downloadStatusMap: ObservableMap<Int, Int>,
                                val level: Int,
                                val city: MKOLSearchRecord) : ViewModel {

    val space: String
        get() = String(CharArray(level * 2) { '\t' })

    val displayDownloadButton = createCompositeObservable(downloadStatusMap) {
        downloadStatusMap.getOrDefault(city.cityID, MapDownloadService.DOWNLOAD_STATUS_IDLE)
                .let { it == MapDownloadService.DOWNLOAD_STATUS_IDLE || it == MapDownloadService.DOWNLOAD_STATUS_PAUSED }
    }

    val displayInProgress = createCompositeObservable(downloadStatusMap) {
        downloadStatusMap.getOrDefault(city.cityID, MapDownloadService.DOWNLOAD_STATUS_IDLE) == MapDownloadService.DOWNLOAD_STATUS_IN_PROGRESS
    }

    val displayPauseButton: ObservableField<Boolean>
        get() = displayInProgress

    val displayDeleteButton = createCompositeObservable(downloadStatusMap) {
        downloadStatusMap.getOrDefault(city.cityID, MapDownloadService.DOWNLOAD_STATUS_IDLE) == MapDownloadService.DOWNLOAD_STATUS_DOWNLOADED
    }

    fun onClickToggleDownload() {
        MapDownloadService.toggleDownload(city.cityID)
    }

    fun onClickDeleteDownload() {
        MapDownloadService.deleteDownload(city.cityID)
    }
}