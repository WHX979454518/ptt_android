package com.xianzhitech.ptt.ui.map

import android.app.Notification
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.support.v7.app.NotificationCompat
import android.widget.Toast
import com.baidu.mapapi.map.offline.MKOLUpdateElement
import com.baidu.mapapi.map.offline.MKOfflineMap
import com.xianzhitech.ptt.BaseApp
import com.xianzhitech.ptt.R
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject
import java.util.concurrent.ConcurrentHashMap


class MapDownloadService : Service(), MapDownloadServiceConnection {
    private lateinit var offlineMap: MKOfflineMap
    private var disposable  :Disposable? = null

    private val downloadInProgressNotification : Notification by lazy {
        NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.offline_map_downloading))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_file_download_black_24dp)
                .build()
    }

    override val downloadStatusMap: BehaviorSubject<MutableMap<Int, Int>> =
            BehaviorSubject.createDefault<MutableMap<Int, Int>>(ConcurrentHashMap())

    override fun onCreate() {
        super.onCreate()

        offlineMap = MKOfflineMap()
        offlineMap.init { type, _ ->
            when (type) {
                MKOfflineMap.TYPE_NEW_OFFLINE -> {
                    syncStatus()
                }

                MKOfflineMap.TYPE_NETWORK_ERROR -> {
                    syncStatus()
                    Toast.makeText(this, R.string.error_download_map, Toast.LENGTH_LONG).show()
                }
            }
        }

        disposable = downloadStatusMap
                .map { it.count { it.value == DOWNLOAD_STATUS_IN_PROGRESS } > 0 }
                .distinctUntilChanged()
                .subscribe { hasProgress ->
                    if (hasProgress) {
                        startForeground(100, downloadInProgressNotification)
                    }
                    else {
                        stopForeground(true)
                    }
                }
    }



    private fun syncStatus() {
        val statusMap = downloadStatusMap.value
        statusMap.clear()

        offlineMap.allUpdateInfo.forEach { info ->
            when (info.status) {
                MKOLUpdateElement.WAITING,
                MKOLUpdateElement.DOWNLOADING -> statusMap[info.cityID] = DOWNLOAD_STATUS_IN_PROGRESS
                MKOLUpdateElement.SUSPENDED -> statusMap[info.cityID] = DOWNLOAD_STATUS_PAUSED
                MKOLUpdateElement.FINISHED -> statusMap[info.cityID] = DOWNLOAD_STATUS_DOWNLOADED
            }
        }

        notifyStatusChanged()
    }

    override fun onDestroy() {
        offlineMap.destroy()

        disposable?.dispose()

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DELETE -> {
                val cityId = intent.getIntExtra(EXTRA_CITY_ID, -1)
                offlineMap.remove(cityId)
                downloadStatusMap.value.remove(cityId)
                notifyStatusChanged()
            }

            ACTION_TOGGLE_DOWNLOAD -> {
                val cityId = intent.getIntExtra(EXTRA_CITY_ID, -1)
                val statusMap = downloadStatusMap.value
                when (statusMap.getOrElse(cityId) { DOWNLOAD_STATUS_IDLE }) {
                    DOWNLOAD_STATUS_IN_PROGRESS -> {
                        offlineMap.pause(cityId)
                        statusMap[cityId] = DOWNLOAD_STATUS_PAUSED
                        notifyStatusChanged()
                    }

                    DOWNLOAD_STATUS_PAUSED,
                    DOWNLOAD_STATUS_IDLE -> {
                        offlineMap.start(cityId)
                        statusMap[cityId] = DOWNLOAD_STATUS_IN_PROGRESS
                        notifyStatusChanged()
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun notifyStatusChanged() {
        downloadStatusMap.onNext(downloadStatusMap.value)
    }

    override fun onBind(intent: Intent?): IBinder {
        return object : Binder(), MapDownloadServiceConnection by this {}
    }

    companion object {

        val downloadStatusMap: Observable<Map<Int, Int>> = Observable.create<MapDownloadServiceConnection> { emitter ->
            val conn = object : ServiceConnection {
                override fun onServiceDisconnected(name: ComponentName?) {}

                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    emitter.onNext((service as MapDownloadServiceConnection))
                }
            }

            BaseApp.instance.bindService(Intent(BaseApp.instance, MapDownloadService::class.java), conn, Context.BIND_AUTO_CREATE)
            emitter.setCancellable { BaseApp.instance.unbindService(conn) }
        }.switchMap { it.downloadStatusMap }
                .replay(1)
                .refCount()

        fun toggleDownload(cityID: Int) {
            BaseApp.instance.startService(Intent(BaseApp.instance, MapDownloadService::class.java)
                    .setAction(ACTION_TOGGLE_DOWNLOAD)
                    .putExtra(EXTRA_CITY_ID, cityID)
            )
        }

        fun deleteDownload(cityID: Int) {
            BaseApp.instance.startService(Intent(BaseApp.instance, MapDownloadService::class.java)
                    .setAction(ACTION_DELETE)
                    .putExtra(EXTRA_CITY_ID, cityID)
            )
        }

        const val DOWNLOAD_STATUS_IDLE = 0
        const val DOWNLOAD_STATUS_PAUSED = 1
        const val DOWNLOAD_STATUS_IN_PROGRESS = 2
        const val DOWNLOAD_STATUS_DOWNLOADED = 3

        private const val ACTION_TOGGLE_DOWNLOAD = "toggle_download"
        private const val ACTION_DELETE = "delete"

        private const val EXTRA_CITY_ID = "city_id"
    }
}

private interface MapDownloadServiceConnection {
    val downloadStatusMap: Observable<out Map<Int, Int>>
}
